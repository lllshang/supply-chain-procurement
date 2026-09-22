package com.dzgylxt.service.impl.purchase;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.approval.ApprovalGateway;
import com.dzgylxt.approval.ApprovalTaskSpec;
import com.dzgylxt.common.BizException;
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
import com.dzgylxt.service.IBudgetSoftCheckService;
import com.dzgylxt.service.IPurchaseApplyService;
import com.dzgylxt.vo.purchase.ApplyDetailRespVO;
import com.dzgylxt.vo.purchase.ApplyItemReqVO;
import com.dzgylxt.vo.purchase.ApplySaveReqVO;
import com.dzgylxt.vo.purchase.BudgetCheckResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购申请服务实现（设计 §2.1）。
 *
 * <p>状态机：DRAFT →(submit 软校验) BUDGET_PENDING →(自动) PURCHASE_PENDING
 * →(两级审批 DEPT_HEAD→PURCHASE_DEPT，本地桩即审即过) APPROVED / REJECTED；
 * REJECTED 修改重提 = 重新发起审批（同 bizId 新任务，旧任务留痕）。</p>
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

        // 预算软校验（<!-- D3 -->：只读、超限仅置 budget_status=2 提示、不写 used_amount）
        BigDecimal totalAmount = estimateTotalAmount(id);
        BudgetCheckResultVO check = budgetSoftCheckService.check(
                apply.getDeptId(), null, null, totalAmount);
        apply.setBudgetStatus(check.getBudgetStatus());

        // DRAFT → BUDGET_PENDING → PURCHASE_PENDING（软校验自动通过，预算升级审批分支 P3 启用 <!-- D3 -->）
        apply.setStatus(PurchaseApplyStatus.PURCHASE_PENDING);
        updateById(apply);

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

    @Override
    public byte[] exportApply(Long id) {
        ApplyDetailRespVO detail = detail(id);
        StringBuilder sb = new StringBuilder();
        sb.append("申请单号,标题,类型,状态,预算状态,期望到货,申请部门,申请人\n");
        PurchaseApply apply = detail.getApply();
        sb.append(csv(apply.getApplyNo())).append(',')
                .append(csv(apply.getTitle())).append(',')
                .append(csv(apply.getType() == null ? "" : apply.getType().getDesc())).append(',')
                .append(csv(apply.getStatus() == null ? "" : apply.getStatus().getDesc())).append(',')
                .append(apply.getBudgetStatus() == null ? 0 : apply.getBudgetStatus()).append(',')
                .append(csv(apply.getExpectedDate() == null ? "" : apply.getExpectedDate().toString())).append(',')
                .append(csv(String.valueOf(apply.getDeptId()))).append(',')
                .append(csv(String.valueOf(apply.getApplicantId()))).append('\n');
        sb.append("\nSKU,采购单位,采购数量,换算率,基本单位数量,预估单价,行类型,备注\n");
        for (PurchaseApplyItem item : detail.getItems()) {
            sb.append(csv(String.valueOf(item.getSkuId()))).append(',')
                    .append(csv(item.getPurchaseUnit())).append(',')
                    .append(csv(String.valueOf(item.getQtyInPurchaseUnit()))).append(',')
                    .append(csv(String.valueOf(item.getConvRateSnapshot()))).append(',')
                    .append(csv(String.valueOf(item.getQtyInBaseUnit()))).append(',')
                    .append(csv(String.valueOf(item.getPriceEstimate()))).append(',')
                    .append(csv(item.getItemType() == null ? ItemType.MATERIAL.getDesc() : item.getItemType().getDesc())).append(',')
                    .append(csv(item.getRemark())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
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
            if (sku == null || sku.getStatus() == null || sku.getStatus() != 0) {
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
    }

    /** CSV 单元格转义。 */
    private String csv(Object value) {
        String s = value == null ? "" : String.valueOf(value);
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}
