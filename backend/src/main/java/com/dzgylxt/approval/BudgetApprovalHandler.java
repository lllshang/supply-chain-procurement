package com.dzgylxt.approval;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.dzgylxt.entity.approval.ApprovalTask;
import com.dzgylxt.entity.budget.BudgetLine;
import com.dzgylxt.entity.order.PurchaseOrder;
import com.dzgylxt.entity.purchase.PurchaseApply;
import com.dzgylxt.enums.PurchaseApplyStatus;
import com.dzgylxt.mapper.approval.ApprovalTaskMapper;
import com.dzgylxt.mapper.budget.BudgetLineMapper;
import com.dzgylxt.mapper.purchase.PurchaseApplyMapper;
import com.dzgylxt.service.IBudgetOccupyService;
import com.dzgylxt.vo.budget.BudgetOccupyCmd;
import com.dzgylxt.vo.budget.OccupyResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * BUDGET 升级审批回调处理器（P3 设计 §4；bizType 共 7 个之一，契约冻结待 P4）。
 *
 * <p>四类负载（payloadJson 持久化于 approval_task，回调侧读取区分）：</p>
 * <ul>
 *   <li><b>申请超支</b>（bizId=applyId，payload.adjust/award/orderChange 均缺省）：
 *       通过→force 超支占用生效 + 申请 BUDGET_PENDING→PURCHASE_PENDING 并补发
 *       PURCHASE_APPLY 两级审批；驳回→BUDGET_PENDING→REJECTED（不占用，修改重提再校验）；</li>
 *   <li><b>月度调整</b>（payload.adjust=true，R8：一律审批）：通过→按 payload.newAmount
 *       生效（写 ADJUST log，前后值留痕）；驳回→不生效（台账维持原额）；</li>
 *   <li><b>定标再校验拦截</b>（payload.award=true，R7）：通过→放行确认（补发 AWARD 审批）；
 *       驳回→定标维持 PENDING_APPROVAL（可调整重提）；</li>
 *   <li><b>订单变更增额拦截</b>（payload.orderChange=true，#47）：通过→force 超支占用
 *       变更差额生效（bizType=ORDER，占用预挂，请重提变更）；驳回→变更维持拦截。</li>
 * </ul>
 */
@Component
public class BudgetApprovalHandler implements ApprovalCallbackHandler {

    private static final Logger log = LoggerFactory.getLogger(BudgetApprovalHandler.class);

    private final ApprovalTaskMapper approvalTaskMapper;
    private final PurchaseApplyMapper applyMapper;
    private final com.dzgylxt.mapper.order.PurchaseOrderMapper orderMapper;
    private final BudgetLineMapper budgetLineMapper;
    private final IBudgetOccupyService budgetOccupyService;
    private final ApprovalGateway approvalGateway;
    private final org.springframework.beans.factory.ObjectProvider<com.dzgylxt.service.IAwardService> awardServiceProvider;

    public BudgetApprovalHandler(ApprovalTaskMapper approvalTaskMapper,
                                 PurchaseApplyMapper applyMapper,
                                 com.dzgylxt.mapper.order.PurchaseOrderMapper orderMapper,
                                 BudgetLineMapper budgetLineMapper,
                                 IBudgetOccupyService budgetOccupyService,
                                 ObjectProvider<ApprovalGateway> gatewayProvider,
                                 org.springframework.beans.factory.ObjectProvider<com.dzgylxt.service.IAwardService> awardServiceProvider) {
        this.approvalTaskMapper = approvalTaskMapper;
        this.applyMapper = applyMapper;
        this.orderMapper = orderMapper;
        this.budgetLineMapper = budgetLineMapper;
        this.budgetOccupyService = budgetOccupyService;
        this.approvalGateway = gatewayProvider.getIfAvailable();
        this.awardServiceProvider = awardServiceProvider;
    }

    @Override
    public String bizType() {
        return "BUDGET";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long taskId, Long bizId, String comment) {
        ApprovalTask task = approvalTaskMapper.selectById(taskId);
        JSONObject payload = parsePayload(task);

        if (payload != null && payload.getBool("adjust", false)) {
            // 月度调整生效（R8）：按 payload.newAmount 落库（ADJUST log 由 adjust 动作补写）
            BudgetLine line = budgetLineMapper.selectById(bizId);
            if (line == null) {
                return;
            }
            BigDecimal newAmount = payload.getBigDecimal("newAmount");
            BigDecimal oldAmount = line.getAmount() == null ? BigDecimal.ZERO : line.getAmount();
            BigDecimal delta = newAmount.subtract(oldAmount);
            int updated = budgetLineMapper.adjustAmount(bizId, newAmount, line.getVersion());
            if (updated == 0) {
                BudgetLine fresh = budgetLineMapper.selectById(bizId);
                updated = budgetLineMapper.adjustAmount(bizId, newAmount, fresh.getVersion());
            }
            if (updated > 0) {
                BudgetOccupyCmd logCmd = new BudgetOccupyCmd();
                logCmd.setBizType(com.dzgylxt.enums.BudgetBizType.ADJUST);
                logCmd.setBizId(bizId);
                logCmd.setAmount(delta);
                logCmd.setRemark("月度调整审批通过（原 " + oldAmount + " → " + newAmount + "）");
                budgetOccupyService.recordAdjustLog(line.getId(), logCmd, delta, oldAmount, newAmount);
            }
            return;
        }

        if (payload != null && payload.getBool("award", false)) {
            // 定标再校验放行（R7）：补发 AWARD 审批（预算仅校验不占用，无占用/回滚动作）
            if (awardServiceProvider.getIfAvailable() != null) {
                awardServiceProvider.getIfAvailable().releaseAfterBudgetApproval(bizId);
            }
            return;
        }

        if (payload != null && payload.getBool("orderChange", false)) {
            // 订单变更增额升级通过（#47）：force 超支占用差额生效（占用预挂，请重提变更）
            Long orderId = payload.getLong("orderId");
            BigDecimal amount = payload.getBigDecimal("amount");
            PurchaseOrder order = orderId == null ? null : orderMapper.selectById(orderId);
            if (order == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }
            BudgetOccupyCmd cmd = new BudgetOccupyCmd();
            cmd.setDeptId(payload.getLong("deptId"));
            // P2b-5：升级 force 占用落到锚点科目行（控制单元=部门×科目×月份）
            cmd.setSubjectId(payload.getLong("subjectId"));
            cmd.setExpectedDate(payload.get("expectedDate") == null ? null
                    : LocalDate.parse(payload.getStr("expectedDate")));
            cmd.setAmount(amount);
            cmd.setBizType(com.dzgylxt.enums.BudgetBizType.ORDER);
            cmd.setBizId(orderId);
            cmd.setForce(true);
            cmd.setRemark("订单变更增额预算升级通过（BUDGET-" + taskId + "），占用预挂，请重提变更");
            OccupyResultVO result = budgetOccupyService.occupy(cmd);
            if (!result.isAvailable()) {
                log.error("[BUDGET] 订单变更增额占用失败 order={}：{}", orderId, result.getMessage());
                return;
            }
            BigDecimal occupied = order.getBudgetOccupied() == null ? BigDecimal.ZERO : order.getBudgetOccupied();
            order.setBudgetOccupied(occupied.add(amount));
            orderMapper.updateById(order);
            return;
        }

        // 申请超支占用生效（行 3）：force 占用 + 推进采购审批
        PurchaseApply apply = applyMapper.selectById(bizId);
        if (apply == null || apply.getStatus() != PurchaseApplyStatus.BUDGET_PENDING) {
            return;
        }
        BudgetOccupyCmd cmd = new BudgetOccupyCmd();
        cmd.setDeptId(apply.getDeptId());
        // QA2-01：超支占用同样落到申请科目行（控制单元=部门×月份×科目）
        cmd.setSubjectId(apply.getBudgetSubjectId());
        cmd.setAmount(payload == null ? BigDecimal.ZERO : payload.getBigDecimal("amount"));
        cmd.setBizType(com.dzgylxt.enums.BudgetBizType.APPLY);
        cmd.setBizId(apply.getId());
        cmd.setExpectedDate(apply.getExpectedDate());
        cmd.setForce(true);
        cmd.setRemark("超预算升级审批通过（BUDGET-" + taskId + "）");
        OccupyResultVO result = budgetOccupyService.occupy(cmd);
        if (!result.isAvailable()) {
            log.error("[BUDGET] 超支占用失败 apply={}：{}", bizId, result.getMessage());
            return;
        }
        apply.setStatus(PurchaseApplyStatus.PURCHASE_PENDING);
        applyMapper.updateById(apply);
        // 补发采购两级审批（行 3：→PURCHASE_PENDING）
        if (approvalGateway != null) {
            com.dzgylxt.approval.ApprovalTaskSpec spec = new com.dzgylxt.approval.ApprovalTaskSpec();
            spec.setBizType("PURCHASE_APPLY");
            spec.setBizId(apply.getId());
            spec.setTitle("采购申请-" + apply.getApplyNo() + "（预算升级后）");
            spec.setApplicant(String.valueOf(apply.getApplicantId()));
            approvalGateway.create(spec);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long taskId, Long bizId, String comment) {
        ApprovalTask task = approvalTaskMapper.selectById(taskId);
        JSONObject payload = parsePayload(task);
        if (payload != null && payload.getBool("adjust", false)) {
            // 调整驳回：不生效（台账维持原额）
            return;
        }
        if (payload != null && (payload.getBool("award", false) || payload.getBool("orderChange", false))) {
            // 定标再校验驳回（R7）/订单变更升级驳回（#47）：业务侧维持拦截态，无状态回写
            return;
        }
        // 申请超支驳回（行 4）：BUDGET_PENDING→REJECTED（未占用，无释放动作）
        PurchaseApply apply = applyMapper.selectById(bizId);
        if (apply == null || apply.getStatus() != PurchaseApplyStatus.BUDGET_PENDING) {
            return;
        }
        apply.setStatus(PurchaseApplyStatus.REJECTED);
        applyMapper.updateById(apply);
    }

    private JSONObject parsePayload(ApprovalTask task) {
        if (task == null || task.getPayloadJson() == null || task.getPayloadJson().isBlank()) {
            return null;
        }
        return JSONUtil.parseObj(task.getPayloadJson());
    }
}
