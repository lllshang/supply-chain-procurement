package com.dzgylxt.approval;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.dzgylxt.entity.approval.ApprovalTask;
import com.dzgylxt.entity.budget.BudgetLine;
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
 * <p>两类负载（payloadJson 持久化于 approval_task，回调侧读取区分）：</p>
 * <ul>
 *   <li><b>申请超支</b>（bizId=applyId，payload.adjust 缺省）：通过→force 超支占用生效 +
 *       申请 BUDGET_PENDING→PURCHASE_PENDING 并补发 PURCHASE_APPLY 两级审批；
 *       驳回→BUDGET_PENDING→REJECTED（不占用，修改重提再校验）；</li>
 *   <li><b>月度调整</b>（bizId=budget_line_id，payload.adjust=true）：通过→按 payload.newAmount
 *       生效（写 ADJUST log）；驳回→不生效（台账维持原额）。</li>
 * </ul>
 */
@Component
public class BudgetApprovalHandler implements ApprovalCallbackHandler {

    private static final Logger log = LoggerFactory.getLogger(BudgetApprovalHandler.class);

    private final ApprovalTaskMapper approvalTaskMapper;
    private final PurchaseApplyMapper applyMapper;
    private final BudgetLineMapper budgetLineMapper;
    private final IBudgetOccupyService budgetOccupyService;
    private final ApprovalGateway approvalGateway;

    public BudgetApprovalHandler(ApprovalTaskMapper approvalTaskMapper,
                                 PurchaseApplyMapper applyMapper,
                                 BudgetLineMapper budgetLineMapper,
                                 IBudgetOccupyService budgetOccupyService,
                                 ObjectProvider<ApprovalGateway> gatewayProvider) {
        this.approvalTaskMapper = approvalTaskMapper;
        this.applyMapper = applyMapper;
        this.budgetLineMapper = budgetLineMapper;
        this.budgetOccupyService = budgetOccupyService;
        this.approvalGateway = gatewayProvider.getIfAvailable();
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
            // 月度调整生效：按 payload.newAmount 落库（ADJUST log 由 adjust 动作补写）
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

        // 申请超支占用生效（行 3）：force 占用 + 推进采购审批
        PurchaseApply apply = applyMapper.selectById(bizId);
        if (apply == null || apply.getStatus() != PurchaseApplyStatus.BUDGET_PENDING) {
            return;
        }
        BudgetOccupyCmd cmd = new BudgetOccupyCmd();
        cmd.setDeptId(apply.getDeptId());
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
