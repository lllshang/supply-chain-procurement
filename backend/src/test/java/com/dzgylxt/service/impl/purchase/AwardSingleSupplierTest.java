package com.dzgylxt.service.impl.purchase;

import com.dzgylxt.common.BizException;
import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.entity.catalog.Sku;
import com.dzgylxt.entity.purchase.Award;
import com.dzgylxt.entity.purchase.Inquiry;
import com.dzgylxt.enums.InquiryStatus;
import com.dzgylxt.enums.ProductStatus;
import com.dzgylxt.mapper.catalog.SkuMapper;
import com.dzgylxt.mapper.catalog.UnitConversionMapper;
import com.dzgylxt.mapper.purchase.AwardItemMapper;
import com.dzgylxt.mapper.purchase.AwardMapper;
import com.dzgylxt.mapper.purchase.InquiryMapper;
import com.dzgylxt.vo.purchase.AwardSaveReqVO;
import com.dzgylxt.vo.supplier.SupplierAdmissionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R2 定标单中标供应商单测（审计修正 R2 锁定）：
 * 一询价单一中标供应商——跨供应商明细拒绝；单头 supplier_id = 唯一中标供应商；
 * 同一询价禁止重复定标。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AwardSingleSupplierTest {

    private static final long INQUIRY_ID = 700001L;
    private static final long SKU_ID = 2102245024849907713L;
    private static final long SUPPLIER_A = 2102245025428721666L;
    private static final long SUPPLIER_B = 2102245025428721667L;

    @Mock
    private InquiryMapper inquiryMapper;

    @Mock
    private SkuMapper skuMapper;

    @Mock
    private UnitConversionMapper unitConversionMapper;

    @Mock
    private AwardItemMapper awardItemMapper;

    @Mock
    private AwardMapper awardMapper;

    @Mock
    private com.dzgylxt.mapper.purchase.QuotationMapper quotationMapper;

    @Mock
    private com.dzgylxt.mapper.purchase.PurchaseApplyMapper applyMapper;

    @Mock
    private com.dzgylxt.service.IBudgetOccupyService budgetOccupyService;

    @Mock
    private com.dzgylxt.service.ISupplierService supplierService;

    @Mock
    private BusinessNoGenerator businessNoGenerator;

    private AwardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = Mockito.spy(new AwardServiceImpl());
        ReflectionTestUtils.setField(service, "inquiryMapper", inquiryMapper);
        ReflectionTestUtils.setField(service, "skuMapper", skuMapper);
        ReflectionTestUtils.setField(service, "unitConversionMapper", unitConversionMapper);
        ReflectionTestUtils.setField(service, "awardItemMapper", awardItemMapper);
        ReflectionTestUtils.setField(service, "quotationMapper", quotationMapper);
        ReflectionTestUtils.setField(service, "applyMapper", applyMapper);
        ReflectionTestUtils.setField(service, "budgetOccupyService", budgetOccupyService);
        ReflectionTestUtils.setField(service, "supplierService", supplierService);
        ReflectionTestUtils.setField(service, "businessNoGenerator", businessNoGenerator);
        // ServiceImpl.count() 走 baseMapper.selectCount（R2 唯一中标查重）
        ReflectionTestUtils.setField(service, "baseMapper", awardMapper);

        Inquiry inquiry = new Inquiry();
        inquiry.setId(INQUIRY_ID);
        inquiry.setStatus(InquiryStatus.CLOSED);
        inquiry.setApplyId(1L);
        when(inquiryMapper.selectById(INQUIRY_ID)).thenReturn(inquiry);
        when(awardMapper.selectCount(any())).thenReturn(0L);

        Sku sku = new Sku();
        sku.setId(SKU_ID);
        sku.setStatus(ProductStatus.NORMAL);
        sku.setBaseUnit("PCS");
        sku.setPurchaseUnit("BOX");
        when(skuMapper.selectById(SKU_ID)).thenReturn(sku);
        when(skuMapper.selectById(anyLong())).thenReturn(sku);
        when(unitConversionMapper.selectCurrentEffective(anyLong(), anyString(), any())).thenReturn(null);
        when(businessNoGenerator.nextNo(anyString())).thenReturn("DB-202609-000001");
        when(awardItemMapper.insert(any(com.dzgylxt.entity.purchase.AwardItem.class))).thenReturn(1);
        doReturn(true).when(service).save(any(Award.class));
        doReturn(true).when(service).updateById(any(Award.class));
    }

    /** R2：按 SKU 拆多供应商（A/B 混排）→ 拒绝（一询价单一中标供应商）。 */
    @Test
    void createAward_multiSupplier_rejected() {
        AwardSaveReqVO req = new AwardSaveReqVO();
        req.setInquiryId(INQUIRY_ID);
        req.setItems(List.of(
                item(SKU_ID, SUPPLIER_A, new BigDecimal("88")),
                item(2102245024849907714L, SUPPLIER_B, new BigDecimal("90"))));

        BizException e = assertThrows(BizException.class, () -> service.createAward(req));
        assertTrue(e.getMessage().contains("一询价单一中标供应商"), e.getMessage());
    }

    /** R2：单供应商多明细行 → 单头 supplier_id = 唯一中标供应商，金额 = Σ 明细（基本单位口径）。 */
    @Test
    void createAward_singleSupplier_headerIsTheSupplier() {
        AwardSaveReqVO req = new AwardSaveReqVO();
        req.setInquiryId(INQUIRY_ID);
        req.setItems(List.of(
                item(SKU_ID, SUPPLIER_A, new BigDecimal("88")),
                item(2102245024849907714L, SUPPLIER_A, new BigDecimal("90"))));

        service.createAward(req);

        ArgumentCaptor<Award> captor = ArgumentCaptor.forClass(Award.class);
        verify(service).save(captor.capture());
        assertEquals(SUPPLIER_A, captor.getValue().getSupplierId(),
                "R2：单头 supplier_id = 唯一中标供应商");
    }

    /** R2：同一询价已存在定标 → 重复定标拒绝。 */
    @Test
    void createAward_duplicateInquiry_rejected() {
        when(awardMapper.selectCount(any())).thenReturn(1L);
        AwardSaveReqVO req = new AwardSaveReqVO();
        req.setInquiryId(INQUIRY_ID);
        req.setItems(List.of(item(SKU_ID, SUPPLIER_A, new BigDecimal("88"))));

        BizException e = assertThrows(BizException.class, () -> service.createAward(req));
        assertTrue(e.getMessage().contains("已存在定标单"), e.getMessage());
    }

    // ---------------- R7 定标预算再校验 ----------------

    /**
     * R7：提交时预算再校验不足 → 拦截（不发起 AWARD 审批），转 BUDGET 升级审批
     * （payload 带 award 标记）；仅校验不重复占用。
     */
    @Test
    void submit_budgetShortfall_createsBudgetUpgradeTask_notAwardTask() {
        com.dzgylxt.approval.ApprovalGateway gateway =
                org.mockito.Mockito.mock(com.dzgylxt.approval.ApprovalGateway.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "approvalGateway", gateway);
        when(supplierService.getAdmission(any(Long.class))).thenReturn(okAdmission());

        com.dzgylxt.entity.purchase.Award award = new com.dzgylxt.entity.purchase.Award();
        award.setId(910L);
        award.setAwardNo("DB-TEST-000910");
        award.setInquiryId(INQUIRY_ID);
        award.setApplyId(1L);
        award.setSupplierId(SUPPLIER_A);
        award.setAmount(new BigDecimal("5000"));
        award.setStatus(com.dzgylxt.enums.AwardStatus.PENDING_APPROVAL);
        when(awardMapper.selectById(910L)).thenReturn(award);

        com.dzgylxt.entity.purchase.AwardItem ai = new com.dzgylxt.entity.purchase.AwardItem();
        ai.setAwardId(910L);
        ai.setSkuId(SKU_ID);
        ai.setSupplierId(SUPPLIER_A);
        ai.setPrice(new BigDecimal("88"));
        ai.setQty(new BigDecimal("12"));
        ai.setQtyInBaseUnit(new BigDecimal("144"));
        when(awardItemMapper.selectList(any())).thenReturn(List.of(ai));
        when(quotationMapper.selectList(any())).thenReturn(List.of());

        com.dzgylxt.entity.purchase.PurchaseApply apply = new com.dzgylxt.entity.purchase.PurchaseApply();
        apply.setId(1L);
        apply.setDeptId(1L);
        apply.setBudgetSubjectId(1001L);
        when(applyMapper.selectById(1L)).thenReturn(apply);
        when(budgetOccupyService.checkOnly(any(com.dzgylxt.vo.budget.BudgetOccupyCmd.class)))
                .thenReturn(com.dzgylxt.vo.budget.OccupyResultVO.blocked(
                        new BigDecimal("1000"), new BigDecimal("4000"), "当月预算余额不足"));

        service.submit(910L);

        // 仅发 BUDGET 升级任务，不发 AWARD 审批；定标维持 PENDING_APPROVAL
        org.mockito.ArgumentCaptor<com.dzgylxt.approval.ApprovalTaskSpec> spec =
                org.mockito.ArgumentCaptor.forClass(com.dzgylxt.approval.ApprovalTaskSpec.class);
        org.mockito.Mockito.verify(gateway).create(spec.capture());
        assertEquals("BUDGET", spec.getValue().getBizType());
        assertTrue(spec.getValue().getPayloadJson().contains("award"), "payload 应带 award 标记");
        verify(budgetOccupyService, org.mockito.Mockito.never())
                .occupy(any(com.dzgylxt.vo.budget.BudgetOccupyCmd.class));
        assertEquals(com.dzgylxt.enums.AwardStatus.PENDING_APPROVAL, award.getStatus());
    }

    private SupplierAdmissionVO okAdmission() {
        SupplierAdmissionVO ok = new SupplierAdmissionVO();
        ok.setQualified(true);
        return ok;
    }

    private AwardSaveReqVO.AwardItemVO item(long skuId, long supplierId, BigDecimal price) {
        AwardSaveReqVO.AwardItemVO vo = new AwardSaveReqVO.AwardItemVO();
        vo.setSkuId(skuId);
        vo.setSupplierId(supplierId);
        vo.setPrice(price);
        vo.setQty(BigDecimal.valueOf(12));
        return vo;
    }
}
