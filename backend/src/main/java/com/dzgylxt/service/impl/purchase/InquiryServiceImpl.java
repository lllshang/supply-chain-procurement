package com.dzgylxt.service.impl.purchase;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.catalog.Sku;
import com.dzgylxt.entity.purchase.Inquiry;
import com.dzgylxt.entity.purchase.InquirySupplier;
import com.dzgylxt.entity.purchase.PurchaseApply;
import com.dzgylxt.entity.purchase.PurchaseApplyItem;
import com.dzgylxt.entity.purchase.Quotation;
import com.dzgylxt.enums.InquiryStatus;
import com.dzgylxt.enums.PurchaseApplyStatus;
import com.dzgylxt.mapper.catalog.SkuMapper;
import com.dzgylxt.mapper.purchase.InquiryMapper;
import com.dzgylxt.mapper.purchase.PurchaseApplyItemMapper;
import com.dzgylxt.mapper.purchase.PurchaseApplyMapper;
import com.dzgylxt.mapper.purchase.QuotationMapper;
import com.dzgylxt.security.UserContext;
import com.dzgylxt.service.IInquiryService;
import com.dzgylxt.service.IInquirySupplierService;
import com.dzgylxt.service.IPriceHistoryService;
import com.dzgylxt.vo.purchase.InquiryComparisonVO;
import com.dzgylxt.vo.purchase.InquirySaveReqVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 询价单服务实现（设计 §2.2 + §7 准入拦截契约）。 */
@Service
public class InquiryServiceImpl extends ServiceImpl<InquiryMapper, Inquiry> implements IInquiryService {

    /** 比价历史价条数（设计 §2.2：近 5 次，可配置）。 */
    private static final int HISTORY_LIMIT = 5;

    @Autowired
    private PurchaseApplyItemMapper applyItemMapper;

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private QuotationMapper quotationMapper;

    @Autowired
    private IPriceHistoryService priceHistoryService;

    @Autowired
    @Lazy
    private IInquirySupplierService inquirySupplierService;

    @Autowired
    private BusinessNoGenerator businessNoGenerator;

    @Autowired
    private PurchaseApplyMapper purchaseApplyMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createInquiry(InquirySaveReqVO req) {
        if (req.getApplyId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "询价必须关联采购申请");
        }
        PurchaseApply apply = purchaseApplyMapper.selectById(req.getApplyId());
        if (apply == null || apply.getStatus() != PurchaseApplyStatus.APPROVED) {
            // 仅 APPROVED 申请可发起询价（设计 §2.2；明细子集的圈定由报价导入时的 SKU 校验兜底）
            throw new BizException(ResultCode.STATUS_INVALID, "仅已审批的申请可发起询价");
        }
        Inquiry inquiry = new Inquiry();
        inquiry.setApplyId(req.getApplyId());
        inquiry.setInquiryNo(businessNoGenerator.nextNo("XJ"));
        inquiry.setStatus(InquiryStatus.DRAFT);
        inquiry.setDeadline(req.getDeadline());
        inquiry.setCreatedByDept(UserContext.getCurrentDeptId());
        inquiry.setRemark(req.getRemark());
        save(inquiry);

        // 初始供应商范围（逐家准入校验 + 快照，与发布同口径）
        if (req.getSupplierIds() != null && !req.getSupplierIds().isEmpty()) {
            inquirySupplierService.addSuppliers(inquiry.getId(), req.getSupplierIds());
        }
        return inquiry.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id, List<Long> supplierIds) {
        Inquiry inquiry = getById(id);
        if (inquiry == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "询价单不存在：" + id);
        }
        if (inquiry.getStatus() != InquiryStatus.DRAFT) {
            throw new BizException(ResultCode.STATUS_INVALID, "仅草稿询价可发布：" + inquiry.getStatus().getDesc());
        }
        // 发布必须 ≥1 家供应商（先补范围再发布）
        List<InquirySupplier> scope = inquirySupplierService.list(
                Wrappers.<InquirySupplier>lambdaQuery().eq(InquirySupplier::getInquiryId, id));
        boolean hasSupplier = (supplierIds != null && !supplierIds.isEmpty()) || !scope.isEmpty();
        if (!hasSupplier) {
            throw new BizException(ResultCode.PARAM_ERROR, "发布询价至少需要 1 家供应商");
        }
        if (supplierIds != null && !supplierIds.isEmpty()) {
            inquirySupplierService.addSuppliers(id, supplierIds);
            scope = inquirySupplierService.list(
                    Wrappers.<InquirySupplier>lambdaQuery().eq(InquirySupplier::getInquiryId, id));
        }
        // 剔除式（QA #25）：逐家准入——不合格剔除（invited=0，快照留痕原因）、合格加入；
        // 此处补齐快照（范围既有行可能为建单时落库，统一刷新一次）
        inquirySupplierService.refreshSnapshots(id);

        // 剔除后有效范围（invited=1）为空才拒绝；混合 [合格,不合格] 不再整体 4000
        long effective = inquirySupplierService.count(
                Wrappers.<InquirySupplier>lambdaQuery()
                        .eq(InquirySupplier::getInquiryId, id)
                        .eq(InquirySupplier::getInvited, 1));
        if (effective == 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "无可用供应商：全部候选均未通过准入校验（原因见范围快照）");
        }

        inquiry.setStatus(InquiryStatus.PUBLISHED);
        updateById(inquiry);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        Inquiry inquiry = getById(id);
        if (inquiry == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "询价单不存在：" + id);
        }
        if (inquiry.getStatus() != InquiryStatus.PUBLISHED) {
            throw new BizException(ResultCode.STATUS_INVALID, "仅已发布询价可截标");
        }
        inquiry.setStatus(InquiryStatus.CLOSED);
        updateById(inquiry);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        Inquiry inquiry = getById(id);
        if (inquiry == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "询价单不存在：" + id);
        }
        if (inquiry.getStatus() != InquiryStatus.DRAFT && inquiry.getStatus() != InquiryStatus.PUBLISHED) {
            throw new BizException(ResultCode.STATUS_INVALID, "仅草稿/已发布询价可取消");
        }
        inquiry.setStatus(InquiryStatus.CANCELLED);
        updateById(inquiry);
    }

    @Override
    public InquiryComparisonVO comparison(Long id) {
        Inquiry inquiry = getById(id);
        if (inquiry == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "询价单不存在：" + id);
        }
        // 询价 SKU 集合 = 申请明细
        List<PurchaseApplyItem> applyItems = applyItemMapper.selectList(
                Wrappers.<PurchaseApplyItem>lambdaQuery().eq(PurchaseApplyItem::getApplyId, inquiry.getApplyId()));

        // 有效报价（invalid=0）
        List<Quotation> quotations = quotationMapper.selectList(
                Wrappers.<Quotation>lambdaQuery()
                        .eq(Quotation::getInquiryId, id)
                        .eq(Quotation::getInvalid, 0));

        InquiryComparisonVO vo = new InquiryComparisonVO();
        vo.setInquiryId(id);
        vo.setInquiryNo(inquiry.getInquiryNo());
        for (PurchaseApplyItem item : applyItems) {
            InquiryComparisonVO.SkuComparison sc = new InquiryComparisonVO.SkuComparison();
            sc.setSkuId(item.getSkuId());
            Sku sku = skuMapper.selectById(item.getSkuId());
            sc.setSkuCode(sku == null ? null : sku.getSkuCode());

            List<Quotation> skuQuotes = quotations.stream()
                    .filter(q -> item.getSkuId().equals(q.getSkuId())).toList();
            sc.setQuotations(skuQuotes);
            if (!skuQuotes.isEmpty()) {
                BigDecimal min = skuQuotes.stream().map(Quotation::getPrice)
                        .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
                BigDecimal max = skuQuotes.stream().map(Quotation::getPrice)
                        .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
                BigDecimal sum = skuQuotes.stream().map(Quotation::getPrice)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                sc.setMinPrice(min);
                sc.setMaxPrice(max);
                sc.setAvgPrice(sum.divide(BigDecimal.valueOf(skuQuotes.size()), 4, RoundingMode.HALF_UP));
            }
            // 历史价（P3 T07）：优先价格库 price_history（仅通过价）；
            // 空库兜底回实时计算（该 SKU 近 5 次有效报价，跨询价时间倒序）
            List<com.dzgylxt.entity.cost.PriceHistory> recorded =
                    priceHistoryService.recent(item.getSkuId(), HISTORY_LIMIT);
            if (!recorded.isEmpty()) {
                for (com.dzgylxt.entity.cost.PriceHistory h : recorded) {
                    InquiryComparisonVO.PriceHistory vo2 = new InquiryComparisonVO.PriceHistory();
                    vo2.setSkuId(h.getSkuId());
                    vo2.setSupplierId(h.getSupplierId());
                    vo2.setPrice(h.getPrice());
                    vo2.setSource("price_history:" + h.getSource().name());
                    vo2.setTime(h.getEffectiveDate() == null ? null : h.getEffectiveDate().toString());
                    sc.getHistory().add(vo2);
                }
            } else {
                List<Quotation> history = quotationMapper.selectList(
                        Wrappers.<Quotation>lambdaQuery()
                                .eq(Quotation::getSkuId, item.getSkuId())
                                .eq(Quotation::getInvalid, 0)
                                .orderByDesc(Quotation::getCreatedAt)
                                .last("LIMIT " + HISTORY_LIMIT));
                for (Quotation q : history) {
                    InquiryComparisonVO.PriceHistory h = new InquiryComparisonVO.PriceHistory();
                    h.setSkuId(q.getSkuId());
                    h.setSupplierId(q.getSupplierId());
                    h.setPrice(q.getPrice());
                    h.setSource("quotation");
                    h.setTime(q.getCreatedAt() == null ? null : q.getCreatedAt().toString());
                    sc.getHistory().add(h);
                }
            }
            vo.getItems().add(sc);
        }
        return vo;
    }
}
