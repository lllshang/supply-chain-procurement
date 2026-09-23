package com.dzgylxt.service.impl.purchase;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.approval.ApprovalGateway;
import com.dzgylxt.approval.ApprovalTaskSpec;
import com.dzgylxt.common.BizException;
import com.dzgylxt.enums.ProductStatus;
import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.catalog.Sku;
import com.dzgylxt.entity.catalog.UnitConversion;
import com.dzgylxt.entity.purchase.Award;
import com.dzgylxt.entity.purchase.AwardItem;
import com.dzgylxt.entity.purchase.Inquiry;
import com.dzgylxt.entity.purchase.Quotation;
import com.dzgylxt.enums.AwardStatus;
import com.dzgylxt.enums.InquiryStatus;
import com.dzgylxt.enums.QuotationStatus;
import com.dzgylxt.mapper.catalog.SkuMapper;
import com.dzgylxt.mapper.catalog.UnitConversionMapper;
import com.dzgylxt.mapper.purchase.AwardItemMapper;
import com.dzgylxt.mapper.purchase.AwardMapper;
import com.dzgylxt.mapper.purchase.InquiryMapper;
import com.dzgylxt.mapper.purchase.QuotationMapper;
import com.dzgylxt.security.UserContext;
import com.dzgylxt.service.IAwardService;
import com.dzgylxt.service.IPriceHistoryService;
import com.dzgylxt.service.ISupplierService;
import com.dzgylxt.vo.purchase.AwardSaveReqVO;
import com.dzgylxt.vo.supplier.SupplierAdmissionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 定标服务实现（设计 §2.4 + §7.1；R2 修订：一询价单一中标供应商）。
 *
 * <p>状态机：PENDING_APPROVAL →(通过) APPROVED（可登记合同）；
 * →(驳回) REJECTED →(调整重提) PENDING_APPROVAL。定标只驱动合同，不生成订单。</p>
 *
 * <p>R2 回退拆标：{@code award} 按 inquiry 唯一中标（单数语义恢复）；
 * {@code award_item} 仅保留同供应商明细行，不再支持按 SKU 拆多供应商；
 * 合同金额 = 定标金额（可校验），无"份额"概念（审计 #14 连带简化）。</p>
 */
@Service
public class AwardServiceImpl extends ServiceImpl<AwardMapper, Award> implements IAwardService {

    /** 审批 bizType（设计 §3）。 */
    public static final String BIZ_TYPE = "AWARD";

    @Autowired
    private AwardItemMapper awardItemMapper;

    @Autowired
    private InquiryMapper inquiryMapper;

    @Autowired
    private QuotationMapper quotationMapper;

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private UnitConversionMapper unitConversionMapper;

    @Autowired
    private ISupplierService supplierService;

    @Autowired
    private ApprovalGateway approvalGateway;

    @Autowired
    private BusinessNoGenerator businessNoGenerator;

    @Autowired
    private IPriceHistoryService priceHistoryService;

    /** 异常价判定：定标价低于历史均价该百分比（%）时标记人工复核提示（Q 默认 10）。 */
    @Value("${app.award.abnormal-price-percent:10}")
    private Integer abnormalPricePercent;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAward(AwardSaveReqVO req) {
        if (req.getInquiryId() == null || req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "定标必须关联询价且至少一条明细");
        }
        Inquiry inquiry = inquiryMapper.selectById(req.getInquiryId());
        if (inquiry == null || inquiry.getStatus() != InquiryStatus.CLOSED) {
            throw new BizException(ResultCode.STATUS_INVALID, "仅已截标询价可定标");
        }
        // R2：award 按 inquiry 唯一中标（单数语义恢复）
        Long existed = count(Wrappers.<Award>lambdaQuery()
                .eq(Award::getInquiryId, req.getInquiryId()));
        if (existed != null && existed > 0) {
            throw new BizException(ResultCode.STATUS_INVALID, "该询价已存在定标单（一询价单一中标供应商）");
        }
        Award award = new Award();
        award.setInquiryId(req.getInquiryId());
        award.setApplyId(req.getApplyId() == null ? inquiry.getApplyId() : req.getApplyId());
        award.setAwardNo(businessNoGenerator.nextNo("DB"));
        award.setStatus(AwardStatus.PENDING_APPROVAL);
        award.setRemark(req.getRemark());
        award.setAmount(BigDecimal.ZERO);
        // R2：单中标供应商（全明细行同供应商），insert 前必须落值
        award.setSupplierId(singleSupplierOf(req.getItems()));
        save(award);

        BigDecimal amount = replaceItems(award, req);
        award.setAmount(amount);
        updateById(award);
        return award.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAwardItems(Long id, AwardSaveReqVO req) {
        Award award = getById(id);
        if (award == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "定标单不存在：" + id);
        }
        if (award.getStatus() != AwardStatus.REJECTED && award.getStatus() != AwardStatus.PENDING_APPROVAL) {
            throw new BizException(ResultCode.STATUS_INVALID, "当前状态不允许调整定标明细：" + award.getStatus().getDesc());
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "定标明细不能为空");
        }
        BigDecimal amount = replaceItems(award, req);
        award.setAmount(amount);
        // 调整重提 → 回到待审批
        award.setStatus(AwardStatus.PENDING_APPROVAL);
        if (req.getRemark() != null) {
            award.setRemark(req.getRemark());
        }
        updateById(award);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(Long id) {
        Award award = getById(id);
        if (award == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "定标单不存在：" + id);
        }
        if (award.getStatus() != AwardStatus.PENDING_APPROVAL) {
            throw new BizException(ResultCode.STATUS_INVALID, "仅待审批定标可提交：" + award.getStatus().getDesc());
        }
        // 准入重校验（设计 §7.1：定标提交必须实时重校，快照仅展示）
        List<AwardItem> items = awardItemMapper.selectList(Wrappers.<AwardItem>lambdaQuery()
                .eq(AwardItem::getAwardId, id));
        items.stream().map(AwardItem::getSupplierId).distinct().forEach(supplierId -> {
            SupplierAdmissionVO admission = supplierService.getAdmission(supplierId);
            if (admission == null || !Boolean.TRUE.equals(admission.getQualified())) {
                String reason = admission == null ? "供应商不存在"
                        : String.join("；", admission.getReasons());
                throw new BizException(ResultCode.PARAM_ERROR,
                        "定标提交准入校验未通过：" + supplierId + "（" + reason + "）");
            }
        });

        // 异常价人工复核提示（低于历史均价 X%）：落 remark
        String abnormal = detectAbnormalPrices(id, items);
        award.setRemark(abnormal == null
                ? (award.getRemark() == null ? "" : award.getRemark())
                : "[人工复核] " + abnormal);
        award.setStatus(AwardStatus.PENDING_APPROVAL);
        updateById(award);

        ApprovalTaskSpec spec = new ApprovalTaskSpec();
        spec.setBizType(BIZ_TYPE);
        spec.setBizId(award.getId());
        spec.setTitle("定标-" + award.getAwardNo());
        spec.setApplicant(UserContext.getCurrentUsername());
        spec.setPayloadJson(JSONUtil.toJsonStr(items.stream()
                .map(i -> new Object() {
                    public final Long skuId = i.getSkuId();
                    public final Long supplierId = i.getSupplierId();
                    public final BigDecimal price = i.getPrice();
                    public final BigDecimal qty = i.getQty();
                }).toList()));
        approvalGateway.create(spec);
        return award.getId();
    }

    @Override
    public List<AwardItem> listItems(Long awardId) {
        return awardItemMapper.selectList(Wrappers.<AwardItem>lambdaQuery()
                .eq(AwardItem::getAwardId, awardId).orderByAsc(AwardItem::getId));
    }

    /** R2 单中标供应商：全部明细行必须同一供应商，返回该供应商（否则拒绝）。 */
    private Long singleSupplierOf(List<AwardSaveReqVO.AwardItemVO> items) {
        Long supplierId = null;
        for (AwardSaveReqVO.AwardItemVO vo : items) {
            if (vo.getSupplierId() == null) {
                throw new BizException(ResultCode.PARAM_ERROR, "定标明细行的供应商必填");
            }
            if (supplierId == null) {
                supplierId = vo.getSupplierId();
            } else if (!supplierId.equals(vo.getSupplierId())) {
                throw new BizException(ResultCode.PARAM_ERROR,
                        "一询价单一中标供应商（R2）：定标明细必须同一供应商，跨供应商请另行询价定标");
            }
        }
        return supplierId;
    }

    /** 落定标明细（含换算快照），返回定标总金额 = Σ price(基本单位口径) × qty_in_base_unit。 */
    private BigDecimal replaceItems(Award award, AwardSaveReqVO req) {
        // R2：明细行仅同供应商（单数语义），跨供应商行直接拒绝
        award.setSupplierId(singleSupplierOf(req.getItems()));
        awardItemMapper.delete(Wrappers.<AwardItem>lambdaQuery()
                .eq(AwardItem::getAwardId, award.getId()));
        LocalDateTime now = LocalDateTime.now();
        BigDecimal amount = BigDecimal.ZERO;
        for (AwardSaveReqVO.AwardItemVO vo : req.getItems()) {
            if (vo.getSkuId() == null
                    || vo.getPrice() == null || vo.getQty() == null
                    || vo.getPrice().compareTo(BigDecimal.ZERO) <= 0
                    || vo.getQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(ResultCode.PARAM_ERROR, "定标明细行的SKU/数量/单价均必填且为正数");
            }
            Sku sku = skuMapper.selectById(vo.getSkuId());
            if (sku == null || sku.getStatus() == null || sku.getStatus() != ProductStatus.NORMAL) {
                throw new BizException(ResultCode.PARAM_ERROR, "SKU 无效或已停用：" + vo.getSkuId());
            }
            String purchaseUnit = sku.getPurchaseUnit() != null ? sku.getPurchaseUnit() : sku.getBaseUnit();
            UnitConversion conv = unitConversionMapper.selectCurrentEffective(vo.getSkuId(), purchaseUnit, now);
            BigDecimal rate = conv == null || conv.getRate() == null ? BigDecimal.ONE : conv.getRate();
            BigDecimal qtyBase = vo.getQty().multiply(rate);

            AwardItem item = new AwardItem();
            item.setAwardId(award.getId());
            item.setSkuId(vo.getSkuId());
            item.setSupplierId(vo.getSupplierId());
            item.setPrice(vo.getPrice());
            item.setQty(vo.getQty());
            item.setQtyInBaseUnit(qtyBase);
            item.setConvRateSnapshot(rate);
            item.setRemark(vo.getRemark());
            awardItemMapper.insert(item);

            amount = amount.add(vo.getPrice().multiply(qtyBase));
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** 异常价检测：定标价低于该 SKU 历史有效报价均价 (1-X%) 时返回提示文本。 */
    private String detectAbnormalPrices(Long awardId, List<AwardItem> items) {
        StringBuilder sb = new StringBuilder();
        for (AwardItem item : items) {
            List<Quotation> history = quotationMapper.selectList(Wrappers.<Quotation>lambdaQuery()
                    .eq(Quotation::getSkuId, item.getSkuId())
                    .ne(Quotation::getStatus, QuotationStatus.REJECTED)
                    .orderByDesc(Quotation::getCreatedAt)
                    .last("LIMIT 5"));
            if (history.isEmpty()) {
                continue;
            }
            BigDecimal avg = history.stream().map(Quotation::getPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(history.size()), 4, RoundingMode.HALF_UP);
            BigDecimal threshold = avg.multiply(BigDecimal.valueOf(100 - abnormalPricePercent))
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            if (item.getPrice().compareTo(threshold) < 0) {
                if (!sb.isEmpty()) {
                    sb.append("；");
                }
                sb.append("SKU ").append(item.getSkuId())
                        .append(" 定标价 ").append(item.getPrice())
                        .append(" 低于历史均价 ").append(avg)
                        .append(" 的 ").append(abnormalPricePercent).append("%，请人工复核");
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    // ---------------- ApprovalCallbackHandler（bizType=AWARD） ----------------

    @Override
    public String bizType() {
        return BIZ_TYPE;
    }

    @Override
    public void onApproved(Long taskId, Long bizId, String comment) {
        Award award = getById(bizId);
        if (award == null || award.getStatus() != AwardStatus.PENDING_APPROVAL) {
            return;
        }
        award.setStatus(AwardStatus.APPROVED);
        updateById(award);
        // 价格库埋点②：定标审批通过（P3 §1.4，逐明细行）
        for (com.dzgylxt.entity.purchase.AwardItem item : awardItemMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.dzgylxt.entity.purchase.AwardItem>lambdaQuery()
                        .eq(com.dzgylxt.entity.purchase.AwardItem::getAwardId, bizId))) {
            priceHistoryService.record(item.getSkuId(), item.getSupplierId(), item.getPrice(),
                    com.dzgylxt.enums.PriceSource.AWARD, "AWARD", bizId,
                    "定标通过-" + award.getAwardNo());
        }
    }

    @Override
    public void onRejected(Long taskId, Long bizId, String comment) {
        Award award = getById(bizId);
        if (award == null || award.getStatus() != AwardStatus.PENDING_APPROVAL) {
            return;
        }
        award.setStatus(AwardStatus.REJECTED);
        updateById(award);
    }
}
