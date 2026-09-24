package com.dzgylxt.service.impl.purchase;

import com.dzgylxt.common.BizException;
import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.entity.catalog.Sku;
import com.dzgylxt.entity.purchase.Award;
import com.dzgylxt.entity.purchase.Inquiry;
import com.dzgylxt.entity.purchase.PurchaseApply;
import com.dzgylxt.enums.AwardStatus;
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

    @Mock
    private com.dzgylxt.mapper.contract.ContractMapper contractMapper;

    @Mock
    private com.dzgylxt.approval.ApprovalGateway approvalGateway;

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
        ReflectionTestUtils.setField(awardService, "contractMapper", contractMapper);
        ReflectionTestUtils.setField(awardService, "approvalGateway", approvalGateway);
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

    // ---------------- P2b-3/4：定标占用生命周期（提交即占/驳回释放/重提幂等/作废闭环） ----------------

    private static final long AWARD_ID = 8001L;

    private Award offlineAward(String amount, AwardStatus status) {
        Award award = new Award();
        award.setId(AWARD_ID);
        award.setAwardNo("DB-TEST-000001");
        award.setDeptId(1L);
        award.setSubjectId(2L);
        award.setAmount(new BigDecimal(amount));
        award.setStatus(status);
        return award;
    }

    /** 提交链路通用桩：准入合格、无历史报价、占用/释放成功、审批网关受理。 */
    private void stubSubmitHappyPath(Award award) {
        doReturn(award).when(awardService).getById(AWARD_ID);
        com.dzgylxt.entity.purchase.AwardItem ai = new com.dzgylxt.entity.purchase.AwardItem();
        ai.setAwardId(AWARD_ID);
        ai.setSkuId(SKU_ID);
        ai.setSupplierId(SUPPLIER_A);
        ai.setPrice(new BigDecimal("88"));
        ai.setQty(new BigDecimal("10"));
        when(awardItemMapper.selectList(any())).thenReturn(List.of(ai));
        com.dzgylxt.vo.supplier.SupplierAdmissionVO admission =
                new com.dzgylxt.vo.supplier.SupplierAdmissionVO();
        admission.setQualified(true);
        when(supplierService.getAdmission(SUPPLIER_A)).thenReturn(admission);
        when(quotationMapper.selectList(any())).thenReturn(List.of());
        when(budgetOccupyService.occupy(any(com.dzgylxt.vo.budget.BudgetOccupyCmd.class)))
                .thenReturn(com.dzgylxt.vo.budget.OccupyResultVO.ok(BigDecimal.ZERO, List.of()));
        when(budgetOccupyService.release(any(com.dzgylxt.vo.budget.BudgetOccupyCmd.class)))
                .thenReturn(com.dzgylxt.vo.budget.OccupyResultVO.ok(BigDecimal.ZERO, List.of()));
        when(approvalGateway.create(any(com.dzgylxt.approval.ApprovalTaskSpec.class))).thenReturn(1L);
    }

    /** P2b-8①：线下定标提交即占——cmd 落 部门×科目×金额×bizType=AWARD×bizId=award。 */
    @Test
    void submit_offline_occupiesBudgetImmediately() {
        Award award = offlineAward("10560", AwardStatus.PENDING_APPROVAL);
        stubSubmitHappyPath(award);
        when(budgetOccupyService.occupiedTotal(any(), any())).thenReturn(BigDecimal.ZERO);

        awardService.submit(AWARD_ID);

        ArgumentCaptor<com.dzgylxt.vo.budget.BudgetOccupyCmd> captor =
                ArgumentCaptor.forClass(com.dzgylxt.vo.budget.BudgetOccupyCmd.class);
        Mockito.verify(budgetOccupyService).occupy(captor.capture());
        com.dzgylxt.vo.budget.BudgetOccupyCmd cmd = captor.getValue();
        assertEquals(1L, cmd.getDeptId());
        assertEquals(2L, cmd.getSubjectId(), "QA2-01：占用必须落到科目行");
        assertEquals(0, cmd.getAmount().compareTo(new BigDecimal("10560")));
        assertEquals(com.dzgylxt.enums.BudgetBizType.AWARD, cmd.getBizType());
        assertEquals(AWARD_ID, cmd.getBizId());
    }

    /** P2b-8②：驳回 → 释放该定标全部占用（RELEASE 流水 + used 回退），防永久假占用。 */
    @Test
    void onRejected_releasesAwardOccupation() {
        Award award = offlineAward("10560", AwardStatus.PENDING_APPROVAL);
        doReturn(award).when(awardService).getById(AWARD_ID);
        when(budgetOccupyService.occupiedTotal(com.dzgylxt.enums.BudgetBizType.AWARD, AWARD_ID))
                .thenReturn(new BigDecimal("10560"));

        awardService.onRejected(99L, AWARD_ID, "不同意");

        assertEquals(AwardStatus.REJECTED, award.getStatus());
        ArgumentCaptor<com.dzgylxt.vo.budget.BudgetOccupyCmd> captor =
                ArgumentCaptor.forClass(com.dzgylxt.vo.budget.BudgetOccupyCmd.class);
        Mockito.verify(budgetOccupyService).release(captor.capture());
        assertEquals(0, captor.getValue().getAmount().compareTo(new BigDecimal("10560")));
        assertEquals(com.dzgylxt.enums.BudgetBizType.AWARD, captor.getValue().getBizType());
        assertEquals(AWARD_ID, captor.getValue().getBizId());
    }

    /** P2b-8③：驳回/改明细后重提幂等——覆盖式先释放旧占用，终态=当前定标金额（非叠加 15840）。 */
    @Test
    void resubmit_idempotent_finalAmountNotStacked() {
        Award award = offlineAward("5280", AwardStatus.PENDING_APPROVAL);
        stubSubmitHappyPath(award);
        // 历史遗留占用 10560（模拟驳回释放遗漏），覆盖式释放读取后无余额
        when(budgetOccupyService.occupiedTotal(com.dzgylxt.enums.BudgetBizType.AWARD, AWARD_ID))
                .thenReturn(new BigDecimal("10560"))
                .thenReturn(BigDecimal.ZERO);

        awardService.submit(AWARD_ID);

        // 旧占用 10560 被释放
        ArgumentCaptor<com.dzgylxt.vo.budget.BudgetOccupyCmd> relCap =
                ArgumentCaptor.forClass(com.dzgylxt.vo.budget.BudgetOccupyCmd.class);
        Mockito.verify(budgetOccupyService).release(relCap.capture());
        assertEquals(0, relCap.getValue().getAmount().compareTo(new BigDecimal("10560")));
        // 终态占用 = 当前有效定标金额 5280（终态金额断言，非 Σlog 守恒可替代）
        ArgumentCaptor<com.dzgylxt.vo.budget.BudgetOccupyCmd> occCap =
                ArgumentCaptor.forClass(com.dzgylxt.vo.budget.BudgetOccupyCmd.class);
        Mockito.verify(budgetOccupyService).occupy(occCap.capture());
        assertEquals(0, occCap.getValue().getAmount().compareTo(new BigDecimal("5280")),
                "终态 used 必须等于当前定标金额，而非历史+新金额叠加");
    }

    /** P2b-3：作废闭环——已登记合同拒绝；无合同时释放占用 + 状态 VOIDED + 留痕。 */
    @Test
    void voidAward_contractGuardAndRelease() {
        Award linked = offlineAward("10560", AwardStatus.APPROVED);
        doReturn(linked).when(awardService).getById(AWARD_ID);
        when(contractMapper.selectCount(any())).thenReturn(1L);
        BizException ex = assertThrows(BizException.class,
                () -> awardService.voidAward(AWARD_ID, "不用了"));
        assertTrue(ex.getMessage().contains("合同"), "已登记合同不可作废：" + ex.getMessage());

        when(contractMapper.selectCount(any())).thenReturn(0L);
        when(budgetOccupyService.occupiedTotal(com.dzgylxt.enums.BudgetBizType.AWARD, AWARD_ID))
                .thenReturn(new BigDecimal("10560"));
        awardService.voidAward(AWARD_ID, "登记错误");
        assertEquals(AwardStatus.VOIDED, linked.getStatus());
        ArgumentCaptor<com.dzgylxt.vo.budget.BudgetOccupyCmd> captor =
                ArgumentCaptor.forClass(com.dzgylxt.vo.budget.BudgetOccupyCmd.class);
        Mockito.verify(budgetOccupyService).release(captor.capture());
        assertEquals(0, captor.getValue().getAmount().compareTo(new BigDecimal("10560")));
    }
}
