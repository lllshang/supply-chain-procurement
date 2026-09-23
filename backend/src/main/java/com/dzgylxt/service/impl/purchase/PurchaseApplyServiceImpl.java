package com.dzgylxt.service.impl.purchase;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.approval.ApprovalGateway;
import com.dzgylxt.approval.ApprovalTaskSpec;
import com.dzgylxt.common.BizException;
import com.dzgylxt.enums.ProductStatus;
import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.common.ParamException;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.catalog.Sku;
import com.dzgylxt.entity.catalog.UnitConversion;
import com.dzgylxt.entity.purchase.PurchaseApply;
import com.dzgylxt.entity.purchase.PurchaseApplyItem;
import com.dzgylxt.enums.ItemType;
import com.dzgylxt.enums.PurchaseApplyStatus;
import com.dzgylxt.enums.PurchaseApplyType;
import com.dzgylxt.mapper.catalog.SkuMapper;
import com.dzgylxt.mapper.catalog.UnitConversionMapper;
import com.dzgylxt.mapper.purchase.PurchaseApplyItemMapper;
import com.dzgylxt.mapper.purchase.PurchaseApplyMapper;
import com.dzgylxt.security.UserContext;
import com.dzgylxt.service.IBudgetOccupyService;
import com.dzgylxt.service.IBudgetSoftCheckService;
import com.dzgylxt.service.IPurchaseApplyService;
import com.dzgylxt.vo.budget.BudgetOccupyCmd;
import com.dzgylxt.vo.budget.OccupyResultVO;
import com.dzgylxt.vo.purchase.ApplyDetailRespVO;
import com.dzgylxt.vo.purchase.ApplyItemReqVO;
import com.dzgylxt.vo.purchase.ApplySaveReqVO;
import com.dzgylxt.vo.purchase.BudgetCheckResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购申请服务实现（设计 §2.1）。
 *
 * <p>状态机（P3 预算硬控 <!-- D3 已落地 -->）：DRAFT →(submit：占用成功) PURCHASE_PENDING
 * →(两级审批 DEPT_HEAD→PURCHASE_DEPT，本地桩即审即过) APPROVED / REJECTED；
 * DRAFT →(submit：余额不足) BUDGET_PENDING →(BUDGET 升级审批：通过=超支占用生效+
 * 补发采购审批 / 驳回=REJECTED)；REJECTED / FULL_ORDER 释放占用（经
 * IBudgetOccupyService 唯一写入口）；REJECTED 修改重提 = 重新校验+占用。</p>
 */
@Service
public class PurchaseApplyServiceImpl extends ServiceImpl<PurchaseApplyMapper, PurchaseApply>
        implements IPurchaseApplyService {

    /** 审批 bizType（设计 §3）。 */
    public static final String BIZ_TYPE = "PURCHASE_APPLY";

    @Autowired
    private PurchaseApplyItemMapper itemMapper;

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private UnitConversionMapper unitConversionMapper;

    @Autowired
    private ApprovalGateway approvalGateway;

    @Autowired
    private IBudgetSoftCheckService budgetSoftCheckService;

    @Autowired
    private IBudgetOccupyService budgetOccupyService;

    @Autowired
    private BusinessNoGenerator businessNoGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createApply(ApplySaveReqVO req) {
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new ParamException("申请标题不能为空");
        }
        PurchaseApply apply = new PurchaseApply();
        apply.setDeptId(UserContext.getCurrentDeptId());
        apply.setApplyNo(businessNoGenerator.nextNo("CG"));
        apply.setTitle(req.getTitle());
        apply.setType(req.getType() == null ? PurchaseApplyType.STANDARD : req.getType());
        apply.setStatus(PurchaseApplyStatus.DRAFT);
        apply.setBudgetStatus(0);
        apply.setApplicantId(UserContext.getCurrentUserId());
        apply.setExpectedDate(req.getExpectedDate());
        apply.setRemark(req.getRemark());
        save(apply);

        saveItems(apply.getId(), req.getItems());
        return apply.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateApply(Long id, ApplySaveReqVO req) {
        PurchaseApply apply = getById(id);
        if (apply == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "采购申请不存在：" + id);
        }
        // 状态机：仅 DRAFT/REJECTED 可编辑
        if (apply.getStatus() != PurchaseApplyStatus.DRAFT
                && apply.getStatus() != PurchaseApplyStatus.REJECTED) {
            throw new BizException(ResultCode.STATUS_INVALID, "当前状态不允许编辑：" + apply.getStatus().getDesc());
        }
        if (req.getTitle() != null && !req.getTitle().isBlank()) {
            apply.setTitle(req.getTitle());
        }
        apply.setType(req.getType() == null ? apply.getType() : req.getType());
        apply.setExpectedDate(req.getExpectedDate());
        apply.setRemark(req.getRemark());
        updateById(apply);

        // 整单替换明细
        itemMapper.delete(Wrappers.<PurchaseApplyItem>lambdaQuery()
                .eq(PurchaseApplyItem::getApplyId, id));
        saveItems(id, req.getItems());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(Long id) {
        PurchaseApply apply = getById(id);
        if (apply == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "采购申请不存在：" + id);
        }
        if (apply.getStatus() != PurchaseApplyStatus.DRAFT
                && apply.getStatus() != PurchaseApplyStatus.REJECTED) {
            throw new BizException(ResultCode.STATUS_INVALID, "当前状态不允许提交：" + apply.getStatus().getDesc());
        }
        List<PurchaseApplyItem> items = itemMapper.selectList(Wrappers.<PurchaseApplyItem>lambdaQuery()
                .eq(PurchaseApplyItem::getApplyId, id));
        if (items.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "申请明细不能为空");
        }

        // 预算硬控（<!-- D3 已落地 P3 -->）：提交即预占用（Q1）；不足→拦截+BUDGET 升级审批（行 1/2）
        BigDecimal totalAmount = estimateTotalAmount(id);
        BudgetOccupyCmd cmd = new BudgetOccupyCmd();
        cmd.setDeptId(apply.getDeptId());
        cmd.setAmount(totalAmount);
        cmd.setBizType(com.dzgylxt.enums.BudgetBizType.APPLY);
        cmd.setBizId(apply.getId());
        cmd.setExpectedDate(apply.getExpectedDate());
        cmd.setRemark("申请提交预占用-" + apply.getApplyNo());
        OccupyResultVO occupy = budgetOccupyService.occupy(cmd);
        apply.setBudgetStatus(occupy.isAvailable() ? 1 : 2);

        if (occupy.isAvailable()) {
            // 行 1：占用成功 → 直接进入采购两级审批
            apply.setStatus(PurchaseApplyStatus.PURCHASE_PENDING);
            updateById(apply);
        } else {
            // 行 2：余额不足/无月度行 → 停 BUDGET_PENDING，发 BUDGET 升级审批（不占用）
            apply.setStatus(PurchaseApplyStatus.BUDGET_PENDING);
            updateById(apply);
            cn.hutool.json.JSONObject payload = new cn.hutool.json.JSONObject();
            payload.set("applyId", apply.getId());
            payload.set("deptId", apply.getDeptId());
            payload.set("amount", totalAmount);
            payload.set("overAmount", occupy.getOverAmount());
            payload.set("balance", occupy.getBalance());
            payload.set("budgetStatus", 2);
            ApprovalTaskSpec budgetSpec = new ApprovalTaskSpec();
            budgetSpec.setBizType("BUDGET");
            budgetSpec.setBizId(apply.getId());
            budgetSpec.setTitle("预算升级-" + apply.getApplyNo());
            budgetSpec.setApplicant(UserContext.getCurrentUsername());
            budgetSpec.setPayloadJson(payload.toString());
            approvalGateway.create(budgetSpec);
            return apply.getId();
        }

        // 发起两级审批（本地桩即审即过；重提=新任务，旧任务留痕）
        ApprovalTaskSpec spec = new ApprovalTaskSpec();
        spec.setBizType(BIZ_TYPE);
        spec.setBizId(apply.getId());
        spec.setTitle("采购申请-" + apply.getApplyNo());
        spec.setApplicant(UserContext.getCurrentUsername());
        spec.setPayloadJson("{\"deptId\":" + apply.getDeptId()
                + ",\"totalAmount\":" + totalAmount
                + ",\"budgetStatus\":" + apply.getBudgetStatus() + "}");
        approvalGateway.create(spec);
        return apply.getId();
    }

    @Override
    public ApplyDetailRespVO detail(Long id) {
        PurchaseApply apply = getById(id);
        if (apply == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "采购申请不存在：" + id);
        }
        ApplyDetailRespVO vo = new ApplyDetailRespVO();
        vo.setApply(apply);
        vo.setItems(itemMapper.selectList(Wrappers.<PurchaseApplyItem>lambdaQuery()
                .eq(PurchaseApplyItem::getApplyId, id).orderByAsc(PurchaseApplyItem::getId)));
        return vo;
    }

    /**
     * 导出申请单（QA #26：与其他导出统一为 OOXML）。
     *
     * <p>两个 Sheet：①「申请单头」1 行；②「明细」含换算快照列
     * （SKU 文本格式，19 位雪花 ID 不经数值转换）。</p>
     */
    @Override
    public byte[] exportApply(Long id) {
        ApplyDetailRespVO detail = detail(id);
        PurchaseApply apply = detail.getApply();

        // EasyExcel head 语义：外层=列、内层=该列的表头行 → 每列各一层
        List<List<String>> headSheet = new java.util.ArrayList<>();
        for (String h : List.of("申请单号", "标题", "类型", "状态", "预算状态", "期望到货", "申请部门", "申请人")) {
            headSheet.add(List.of(h));
        }
        List<List<Object>> headData = new java.util.ArrayList<>();
        headData.add(List.of(
                nvl(apply.getApplyNo()),
                nvl(apply.getTitle()),
                apply.getType() == null ? "" : apply.getType().getDesc(),
                apply.getStatus() == null ? "" : apply.getStatus().getDesc(),
                apply.getBudgetStatus() == null ? 0 : apply.getBudgetStatus(),
                apply.getExpectedDate() == null ? "" : apply.getExpectedDate().toString(),
                String.valueOf(apply.getDeptId()),
                String.valueOf(apply.getApplicantId())));

        List<List<String>> itemHead = new java.util.ArrayList<>();
        for (String h : List.of("SKU", "采购单位", "采购数量", "换算率", "基本单位数量", "预估单价", "行类型", "备注")) {
            itemHead.add(List.of(h));
        }
        List<List<Object>> itemData = new java.util.ArrayList<>();
        for (PurchaseApplyItem item : detail.getItems()) {
            itemData.add(List.of(
                    String.valueOf(item.getSkuId()),
                    nvl(item.getPurchaseUnit()),
                    String.valueOf(item.getQtyInPurchaseUnit()),
                    String.valueOf(item.getConvRateSnapshot()),
                    String.valueOf(item.getQtyInBaseUnit()),
                    String.valueOf(item.getPriceEstimate()),
                    item.getItemType() == null ? ItemType.MATERIAL.getDesc() : item.getItemType().getDesc(),
                    nvl(item.getRemark())));
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            com.alibaba.excel.ExcelWriter writer = EasyExcel.write(out).build();
            com.alibaba.excel.write.metadata.WriteSheet sheet1 = EasyExcel.writerSheet("申请单头")
                    .head(headSheet).build();
            com.alibaba.excel.write.metadata.WriteSheet sheet2 = EasyExcel.writerSheet("明细")
                    .head(itemHead).build();
            writer.write(headData, sheet1);
            writer.write(itemData, sheet2);
            writer.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "申请单导出失败");
        }
    }

    /** null 安全字符串。 */
    private String nvl(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @Override
    public BigDecimal estimateTotalAmount(Long applyId) {
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseApplyItem item : itemMapper.selectList(Wrappers.<PurchaseApplyItem>lambdaQuery()
                .eq(PurchaseApplyItem::getApplyId, applyId))) {
            BigDecimal qty = item.getQtyInPurchaseUnit() == null ? BigDecimal.ZERO : item.getQtyInPurchaseUnit();
            BigDecimal price = item.getPriceEstimate();
            if (price == null) {
                Sku sku = skuMapper.selectById(item.getSkuId());
                price = sku == null ? BigDecimal.ZERO
                        : (sku.getStandardPrice() == null ? BigDecimal.ZERO : sku.getStandardPrice());
            }
            total = total.add(qty.multiply(price));
        }
        return total;
    }

    /** 保存明细：校验有效 SKU（status=0）、逐行落换算快照（selectCurrentEffective，应用时钟）。 */
    private void saveItems(Long applyId, List<ApplyItemReqVO> items) {
        if (items == null || items.isEmpty()) {
            // 兼容 P1 前端仅建头的表单（{title,type}）：明细留空，提交前必须补明细
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (ApplyItemReqVO req : items) {
            if (req.getSkuId() == null || req.getQty() == null
                    || req.getQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(ResultCode.PARAM_ERROR, "明细行数量必须大于 0");
            }
            Sku sku = skuMapper.selectById(req.getSkuId());
            if (sku == null || sku.getStatus() == null || sku.getStatus() != ProductStatus.NORMAL) {
                throw new BizException(ResultCode.PARAM_ERROR, "SKU 无效或已停用：" + req.getSkuId());
            }
            String purchaseUnit = req.getPurchaseUnit() != null ? req.getPurchaseUnit()
                    : (sku.getPurchaseUnit() != null ? sku.getPurchaseUnit() : sku.getBaseUnit());
            // 换算快照：取 unit_conversion 当前生效版本（应用时钟，P1-1 口径）；无换算记录按 1:1
            UnitConversion conv = unitConversionMapper.selectCurrentEffective(req.getSkuId(), purchaseUnit, now);
            BigDecimal rate = conv == null || conv.getRate() == null ? BigDecimal.ONE : conv.getRate();
            BigDecimal qtyBase = req.getQty().multiply(rate);

            PurchaseApplyItem item = new PurchaseApplyItem();
            item.setApplyId(applyId);
            item.setSkuId(req.getSkuId());
            item.setPurchaseUnit(purchaseUnit);
            item.setQtyInPurchaseUnit(req.getQty());
            item.setQtyInBaseUnit(qtyBase);
            item.setConvRateSnapshot(rate);
            item.setPriceEstimate(req.getPriceEstimate());
            item.setItemType(req.getItemType() == null ? ItemType.MATERIAL : req.getItemType());
            item.setRemark(req.getRemark());
            // 余量口径（三重校验规则③）：基本单位
            item.setQty(qtyBase);
            item.setApplyQty(qtyBase);
            item.setOrderedQty(BigDecimal.ZERO);
            item.setRemainQty(qtyBase);
            item.setVersion(0);
            itemMapper.insert(item);
        }
    }

    // ---------------- ApprovalCallbackHandler（bizType=PURCHASE_APPLY） ----------------

    @Override
    public String bizType() {
        return BIZ_TYPE;
    }

    @Override
    public void onApproved(Long taskId, Long bizId, String comment) {
        PurchaseApply apply = getById(bizId);
        // 幂等兜底：仅 PURCHASE_PENDING 可推进
        if (apply == null || apply.getStatus() != PurchaseApplyStatus.PURCHASE_PENDING) {
            return;
        }
        apply.setStatus(PurchaseApplyStatus.APPROVED);
        updateById(apply);
    }

    @Override
    public void onRejected(Long taskId, Long bizId, String comment) {
        PurchaseApply apply = getById(bizId);
        if (apply == null || apply.getStatus() != PurchaseApplyStatus.PURCHASE_PENDING) {
            return;
        }
        apply.setStatus(PurchaseApplyStatus.REJECTED);
        updateById(apply);
        // 行 5：申请驳回释放全部占用（按日志余额，经唯一写入口 <!-- D3 已落地 -->）
        BigDecimal occupied = budgetOccupyService.occupiedTotal(
                com.dzgylxt.enums.BudgetBizType.APPLY, bizId);
        if (occupied.compareTo(BigDecimal.ZERO) > 0) {
            BudgetOccupyCmd releaseCmd = new BudgetOccupyCmd();
            releaseCmd.setDeptId(apply.getDeptId());
            releaseCmd.setAmount(occupied);
            releaseCmd.setBizType(com.dzgylxt.enums.BudgetBizType.APPLY);
            releaseCmd.setBizId(bizId);
            releaseCmd.setRemark("申请驳回释放-" + apply.getApplyNo());
            budgetOccupyService.release(releaseCmd);
        }
    }

    /**
     * 作废申请（P3 设计 §2 行10，QA #36）：未转单完成前可作废；
     * 全部预算占用按日志余额释放（经唯一写入口，保持 Σlog==used_amount 守恒）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeApply(Long id, String reason) {
        PurchaseApply apply = getById(id);
        if (apply == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "采购申请不存在：" + id);
        }
        if (apply.getStatus() == PurchaseApplyStatus.FULL_ORDER
                || apply.getStatus() == PurchaseApplyStatus.CLOSED) {
            throw new BizException(ResultCode.STATUS_INVALID,
                    "当前状态不允许作废：" + apply.getStatus().getDesc());
        }
        apply.setStatus(PurchaseApplyStatus.CLOSED);
        apply.setRemark(reason == null ? "作废" : apply.getRemark() == null
                ? "作废：" + reason : apply.getRemark() + "；作废：" + reason);
        updateById(apply);
        // 行 10：作废释放全部占用（按日志余额，FULL_ORDER 已全额转移无可释放）
        BigDecimal occupied = budgetOccupyService.occupiedTotal(
                com.dzgylxt.enums.BudgetBizType.APPLY, id);
        if (occupied.compareTo(BigDecimal.ZERO) > 0) {
            BudgetOccupyCmd releaseCmd = new BudgetOccupyCmd();
            releaseCmd.setDeptId(apply.getDeptId());
            releaseCmd.setAmount(occupied);
            releaseCmd.setBizType(com.dzgylxt.enums.BudgetBizType.APPLY);
            releaseCmd.setBizId(id);
            releaseCmd.setRemark("申请作废释放-" + apply.getApplyNo());
            budgetOccupyService.release(releaseCmd);
        }
    }
}
