package com.dzgylxt.service.impl.purchase;

import com.dzgylxt.common.BizException;
import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.entity.catalog.Sku;
import com.dzgylxt.entity.purchase.Award;
import com.dzgylxt.entity.purchase.Inquiry;
import com.dzgylxt.entity.purchase.PurchaseApply;
import com.dzgylxt.enums.ProductStatus;
import com.dzgylxt.enums.PurchaseApplyStatus;
import com.dzgylxt.enums.PurchaseApplyType;
import com.dzgylxt.mapper.catalog.SkuMapper;
import com.dzgylxt.mapper.catalog.UnitConversionMapper;
import com.dzgylxt.mapper.purchase.AwardItemMapper;
import com.dzgylxt.mapper.purchase.AwardMapper;
import com.dzgylxt.mapper.purchase.InquiryMapper;
import com.dzgylxt.mapper.purchase.PurchaseApplyMapper;
import com.dzgylxt.vo.purchase.AwardSaveReqVO;
import com.dzgylxt.vo.purchase.InquirySaveReqVO;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * P2b 扩展链路单测（docs/P2b扩展链路规格设计.md）：
 * ① D1：DAILY 日常采购禁止发询价（免比价链路）；
 * ② D9：独立寻 source_reason 必填（BR-07）、无 applyId 落 sourceType=OFFLINE；
 * ③ B3：FULL_ORDER 申请可再次询价（补充采购）；
 * ④ D9/CP-11：线下定标登记（inquiryId=null）必填部门×科目，成功落锚点。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class P2bExtensionTest {

    private static final long INQUIRY_ID = 700101L;
    private static final long APPLY_ID = 700102L;
    private static final long SKU_ID = 2102245024849907713L;
    private static final long SUPPLIER_A = 2102245025428721666L;

    @Mock
    private InquiryMapper inquiryMapper;

    @Mock
    private PurchaseApplyMapper purchaseApplyMapper;

    @Mock
    private BusinessNoGenerator businessNoGenerator;

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

    private InquiryServiceImpl inquiryService;
    private AwardServiceImpl awardService;

    @BeforeEach
    void setUp() {
        inquiryService = Mockito.spy(new InquiryServiceImpl());
        ReflectionTestUtils.setField(inquiryService, "purchaseApplyMapper", purchaseApplyMapper);
        ReflectionTestUtils.setField(inquiryService, "businessNoGenerator", businessNoGenerator);
        ReflectionTestUtils.setField(inquiryService, "baseMapper", inquiryMapper);
        doReturn(true).when(inquiryService).save(any(Inquiry.class));

        awardService = Mockito.spy(new AwardServiceImpl());
        ReflectionTestUtils.setField(awardService, "inquiryMapper", inquiryMapper);
        ReflectionTestUtils.setField(awardService, "skuMapper", skuMapper);
        ReflectionTestUtils.setField(awardService, "unitConversionMapper", unitConversionMapper);
        ReflectionTestUtils.setField(awardService, "awardItemMapper", awardItemMapper);
        ReflectionTestUtils.setField(awardService, "quotationMapper", quotationMapper);
        ReflectionTestUtils.setField(awardService, "applyMapper", applyMapper);
        ReflectionTestUtils.setField(awardService, "budgetOccupyService", budgetOccupyService);
        ReflectionTestUtils.setField(awardService, "supplierService", supplierService);
        ReflectionTestUtils.setField(awardService, "businessNoGenerator", businessNoGenerator);
        ReflectionTestUtils.setField(awardService, "baseMapper", awardMapper);

        Sku sku = new Sku();
        sku.setId(SKU_ID);
        sku.setStatus(ProductStatus.NORMAL);
        sku.setBaseUnit("PCS");
        sku.setPurchaseUnit("BOX");
        when(skuMapper.selectById(anyLong())).thenReturn(sku);
        when(unitConversionMapper.selectCurrentEffective(anyLong(), anyString(), any())).thenReturn(null);
        when(businessNoGenerator.nextNo(anyString())).thenReturn("P2B-202609-000001");
        when(awardItemMapper.insert(any(com.dzgylxt.entity.purchase.AwardItem.class))).thenReturn(1);
        doReturn(true).when(awardService).save(any(Award.class));
        doReturn(true).when(awardService).updateById(any(Award.class));
    }

    private PurchaseApply apply(PurchaseApplyType type, PurchaseApplyStatus status) {
        PurchaseApply apply = new PurchaseApply();
        apply.setId(APPLY_ID);
        apply.setType(type);
        apply.setStatus(status);
        return apply;
    }

    private AwardSaveReqVO.AwardItemVO item(long skuId, long supplierId, String price) {
        AwardSaveReqVO.AwardItemVO vo = new AwardSaveReqVO.AwardItemVO();
        vo.setSkuId(skuId);
        vo.setSupplierId(supplierId);
        vo.setPrice(new BigDecimal(price));
        vo.setQty(new BigDecimal("10"));
        return vo;
    }

    /** ① D1：DAILY 日常采购禁止发询价（免比价链路，关联合同下单）。 */
    @Test
    void createInquiry_daily_rejected() {
        when(purchaseApplyMapper.selectById(APPLY_ID))
                .thenReturn(apply(PurchaseApplyType.DAILY, PurchaseApplyStatus.APPROVED));
        InquirySaveReqVO req = new InquirySaveReqVO();
        req.setApplyId(APPLY_ID);
        BizException ex = assertThrows(BizException.class, () -> inquiryService.createInquiry(req));
        assertTrue(ex.getMessage().contains("免比价"), "应提示免比价链路：" + ex.getMessage());
    }

    /** ② D9：独立寻源缺寻源原因 → 拒绝（BR-07）。 */
    @Test
    void createInquiry_offline_missingReason_rejected() {
        InquirySaveReqVO req = new InquirySaveReqVO();
        req.setSourceType("OFFLINE");
        BizException ex = assertThrows(BizException.class, () -> inquiryService.createInquiry(req));
        assertTrue(ex.getMessage().contains("寻源原因"), "应提示寻源原因必填：" + ex.getMessage());
    }

    /** ② D9：独立寻源带原因 → 成功落 sourceType=OFFLINE、applyId=null。 */
    @Test
    void createInquiry_offline_ok() {
        InquirySaveReqVO req = new InquirySaveReqVO();
        req.setSourceType("OFFLINE");
        req.setSourceReason("年度寻源：耗材价格到期重新比选");
        inquiryService.createInquiry(req);
        ArgumentCaptor<Inquiry> captor = ArgumentCaptor.forClass(Inquiry.class);
        Mockito.verify(inquiryService).save(captor.capture());
        assertEquals("OFFLINE", captor.getValue().getSourceType());
        assertNull(captor.getValue().getApplyId());
        assertEquals("年度寻源：耗材价格到期重新比选", captor.getValue().getSourceReason());
    }

    /** ③ B3：FULL_ORDER 申请可再次询价（补充采购）。 */
    @Test
    void createInquiry_fullOrder_ok() {
        when(purchaseApplyMapper.selectById(APPLY_ID))
                .thenReturn(apply(PurchaseApplyType.STANDARD, PurchaseApplyStatus.FULL_ORDER));
        InquirySaveReqVO req = new InquirySaveReqVO();
        req.setApplyId(APPLY_ID);
        inquiryService.createInquiry(req);
        ArgumentCaptor<Inquiry> captor = ArgumentCaptor.forClass(Inquiry.class);
        Mockito.verify(inquiryService).save(captor.capture());
        assertEquals("APPLY", captor.getValue().getSourceType());
        assertEquals(APPLY_ID, captor.getValue().getApplyId());
    }

    /** ④ D9/CP-11：线下定标缺预算科目 → 拒绝。 */
    @Test
    void createAward_offline_missingSubject_rejected() {
        AwardSaveReqVO req = new AwardSaveReqVO();
        req.setInquiryId(null);
        req.setDeptId(1L);
        req.setItems(List.of(item(SKU_ID, SUPPLIER_A, "88")));
        BizException ex = assertThrows(BizException.class, () -> awardService.createAward(req));
        assertTrue(ex.getMessage().contains("预算科目"), "应提示预算科目必填：" + ex.getMessage());
    }

    /** ④ D9/CP-11：线下定标登记成功 → award 落 deptId/subjectId、inquiryId=null。 */
    @Test
    void createAward_offline_ok() {
        AwardSaveReqVO req = new AwardSaveReqVO();
        req.setInquiryId(null);
        req.setDeptId(1L);
        req.setSubjectId(2L);
        req.setItems(List.of(item(SKU_ID, SUPPLIER_A, "88")));
        awardService.createAward(req);
        ArgumentCaptor<Award> captor = ArgumentCaptor.forClass(Award.class);
        Mockito.verify(awardService).save(captor.capture());
        assertEquals(1L, captor.getValue().getDeptId());
        assertEquals(2L, captor.getValue().getSubjectId());
        assertNull(captor.getValue().getInquiryId());
        assertEquals(SUPPLIER_A, captor.getValue().getSupplierId());
    }
}
