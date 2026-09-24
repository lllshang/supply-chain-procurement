package com.dzgylxt.service.impl.purchase;

import com.dzgylxt.approval.ApprovalGateway;
import com.dzgylxt.approval.ApprovalTaskSpec;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.common.RedisLockUtil;
import com.dzgylxt.entity.catalog.Sku;
import com.dzgylxt.entity.contract.Contract;
import com.dzgylxt.entity.order.OrderItem;
import com.dzgylxt.entity.order.PurchaseOrder;
import com.dzgylxt.entity.purchase.Award;
import com.dzgylxt.entity.purchase.AwardItem;
import com.dzgylxt.enums.AwardStatus;
import com.dzgylxt.enums.BudgetAction;
import com.dzgylxt.enums.BudgetBizType;
import com.dzgylxt.enums.ContractStatus;
import com.dzgylxt.enums.OrderStatus;
import com.dzgylxt.enums.ProductStatus;
import com.dzgylxt.mapper.approval.ApprovalTaskMapper;
import com.dzgylxt.mapper.catalog.SkuMapper;
import com.dzgylxt.mapper.catalog.UnitConversionMapper;
import com.dzgylxt.mapper.contract.ContractMapper;
import com.dzgylxt.mapper.order.OrderChangeMapper;
import com.dzgylxt.mapper.order.OrderItemMapper;
import com.dzgylxt.mapper.order.PurchaseOrderMapper;
import com.dzgylxt.mapper.purchase.AwardItemMapper;
import com.dzgylxt.mapper.purchase.AwardMapper;
import com.dzgylxt.mapper.purchase.QuotationMapper;
import com.dzgylxt.service.IBudgetOccupyService;
import com.dzgylxt.service.ISupplierService;
import com.dzgylxt.vo.budget.BudgetOccupyCmd;
import com.dzgylxt.vo.budget.OccupyResultVO;
import com.dzgylxt.vo.order.OrderChangeReqVO;
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
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P2b-3/4/5/6/8 预算生命周期专项单测：
 * <ul>
 *   <li>① D9 线下定标提交即占（AWARD 占用落 dept×subject 锚点）；</li>
 *   <li>② 驳回释放（RELEASE 负向回冲）+ 重提<b>终态金额</b>断言（不叠加双占）；</li>
 *   <li>③ 作废端点：释放占用 + VOIDED + 已登记合同拒绝；</li>
 *   <li>④ 无申请来源订单变更超额拒绝（锚点回填链 + 拦截 + BUDGET 升级任务）；</li>
 *   <li>⑤ 无锚点订单变更增额硬控拦截（禁止静默绕过）。</li>
 * </ul>
 *
 * <p>QA 教训（P2b-4）：占用类路径必须断言<b>终态金额</b>——Σlog==used 守恒不能替代
 * 业务正确性断言（used 终态必须等于当前有效定标金额，而非历次提交叠加）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class P2bBudgetLifecycleTest {

    private static final long AWARD_ID = 8001L;
    private static final long CONTRACT_ID = 8002L;
    private static final long ORDER_ID = 8003L;
    private static final long ORDER_ITEM_ID = 8004L;
    private static final long DEPT_ID = 1L;
    private static final long SUBJECT_ID = 2L;
    private static final long SKU_ID = 2102245024849907713L;
    private static final long SUPPLIER_A = 2102245025428721666L;
    private static final BigDecimal AMOUNT_V1 = new BigDecimal("10560");
    private static final BigDecimal AMOUNT_V2 = new BigDecimal("5280");

    @Mock
    private AwardMapper awardMapper;
    @Mock
    private AwardItemMapper awardItemMapper;
    @Mock
    private ContractMapper contractMapper;
    @Mock
    private QuotationMapper quotationMapper;
    @Mock
    private com.dzgylxt.mapper.purchase.PurchaseApplyMapper applyMapper;
    @Mock
    private SkuMapper skuMapper;
    @Mock
    private UnitConversionMapper unitConversionMapper;
    @Mock
    private IBudgetOccupyService budgetOccupyService;
    @Mock
    private ISupplierService supplierService;
    @Mock
    private ApprovalGateway approvalGateway;
    @Mock
    private BusinessNoGenerator businessNoGenerator;

    @Mock
    private PurchaseOrderMapper purchaseOrderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private OrderChangeMapper orderChangeMapper;
    @Mock
    private ApprovalTaskMapper approvalTaskMapper;
    @Mock
    private RedisLockUtil redisLockUtil;

    private AwardServiceImpl awardService;
    private com.dzgylxt.service.impl.order.OrderServiceImpl orderService;
    private Award award;

    @BeforeEach
    void setUp() {
        awardService = Mockito.spy(new AwardServiceImpl());
        ReflectionTestUtils.setField(awardService, "skuMapper", skuMapper);
        ReflectionTestUtils.setField(awardService, "unitConversionMapper", unitConversionMapper);
        ReflectionTestUtils.setField(awardService, "awardItemMapper", awardItemMapper);
        ReflectionTestUtils.setField(awardService, "quotationMapper", quotationMapper);
        ReflectionTestUtils.setField(awardService, "applyMapper", applyMapper);
        ReflectionTestUtils.setField(awardService, "contractMapper", contractMapper);
        ReflectionTestUtils.setField(awardService, "budgetOccupyService", budgetOccupyService);
        ReflectionTestUtils.setField(awardService, "supplierService", supplierService);
        ReflectionTestUtils.setField(awardService, "approvalGateway", approvalGateway);
        ReflectionTestUtils.setField(awardService, "businessNoGenerator", businessNoGenerator);
        ReflectionTestUtils.setField(awardService, "baseMapper", awardMapper);
        doReturn(true).when(awardService).updateById(any(Award.class));
        when(awardItemMapper.insert(any(AwardItem.class))).thenReturn(1);
        when(awardItemMapper.delete(any())).thenReturn(1);
        when(businessNoGenerator.nextNo(anyString())).thenReturn("DB-TEST-000001");
        when(approvalGateway.create(any(ApprovalTaskSpec.class))).thenReturn(1L);
        when(budgetOccupyService.release(any(BudgetOccupyCmd.class)))
                .thenAnswer(inv -> OccupyResultVO.ok(inv.getArgument(0, BudgetOccupyCmd.class).getAmount(), List.of()));
        when(budgetOccupyService.occupy(any(BudgetOccupyCmd.class)))
                .thenAnswer(inv -> OccupyResultVO.ok(
                        inv.getArgument(0, BudgetOccupyCmd.class).getAmount(), List.of()));

        Sku sku = new Sku();
        sku.setId(SKU_ID);
        sku.setStatus(ProductStatus.NORMAL);
        sku.setBaseUnit("PCS");
        sku.setPurchaseUnit("BOX");
        when(skuMapper.selectById(anyLong())).thenReturn(sku);
        when(unitConversionMapper.selectCurrentEffective(anyLong(), anyString(), any())).thenReturn(null);
        when(quotationMapper.selectList(any())).thenReturn(List.of());
        SupplierAdmissionVO admission = new SupplierAdmissionVO();
        admission.setQualified(Boolean.TRUE);
        when(supplierService.getAdmission(anyLong())).thenReturn(admission);

        // D9 线下定标（锚点=award）：dept×subject，金额 10560，待审批
        award = new Award();
        award.setId(AWARD_ID);
        award.setAwardNo("DB-TEST-000001");
        award.setInquiryId(null);
        award.setApplyId(null);
        award.setDeptId(DEPT_ID);
        award.setSubjectId(SUBJECT_ID);
        award.setSupplierId(SUPPLIER_A);
        award.setAmount(AMOUNT_V1);
        award.setStatus(AwardStatus.PENDING_APPROVAL);
        doReturn(award).when(awardService).getById(AWARD_ID);

        // ---- OrderServiceImpl（④⑤ 用例）----
        orderService = new com.dzgylxt.service.impl.order.OrderServiceImpl();
        ReflectionTestUtils.setField(orderService, "contractMapper", contractMapper);
        ReflectionTestUtils.setField(orderService, "awardMapper", awardMapper);
        ReflectionTestUtils.setField(orderService, "applyMapper", applyMapper);
        ReflectionTestUtils.setField(orderService, "orderItemMapper", orderItemMapper);
        ReflectionTestUtils.setField(orderService, "orderChangeMapper", orderChangeMapper);
        ReflectionTestUtils.setField(orderService, "applyItemMapper",
                Mockito.mock(com.dzgylxt.mapper.purchase.PurchaseApplyItemMapper.class));
        ReflectionTestUtils.setField(orderService, "inquiryMapper",
                Mockito.mock(com.dzgylxt.mapper.purchase.InquiryMapper.class));
        ReflectionTestUtils.setField(orderService, "awardItemMapper", awardItemMapper);
        ReflectionTestUtils.setField(orderService, "unitConversionMapper", unitConversionMapper);
        ReflectionTestUtils.setField(orderService, "redisLockUtil", redisLockUtil);
        ReflectionTestUtils.setField(orderService, "budgetOccupyService", budgetOccupyService);
        ReflectionTestUtils.setField(orderService, "priceHistoryService",
                Mockito.mock(com.dzgylxt.service.IPriceHistoryService.class));
        ReflectionTestUtils.setField(orderService, "arrivalMapper",
                Mockito.mock(com.dzgylxt.mapper.order.ArrivalMapper.class));
        ReflectionTestUtils.setField(orderService, "arrivalItemMapper",
                Mockito.mock(com.dzgylxt.mapper.order.ArrivalItemMapper.class));
        ReflectionTestUtils.setField(orderService, "baseMapper", purchaseOrderMapper);
        ReflectionTestUtils.setField(orderService, "approvalGateway", approvalGateway);
        ReflectionTestUtils.setField(orderService, "approvalTaskMapper", approvalTaskMapper);
        ReflectionTestUtils.setField(orderService, "businessNoGenerator", businessNoGenerator);
        when(purchaseOrderMapper.updateById(any(PurchaseOrder.class))).thenReturn(1);
        when(orderItemMapper.updateById(any(OrderItem.class))).thenReturn(1);
        when(orderChangeMapper.insert(any(com.dzgylxt.entity.order.OrderChange.class))).thenReturn(1);
        when(approvalTaskMapper.selectList(any())).thenReturn(List.of());
        when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn("token");
    }

    private AwardSaveReqVO offlineReq(String price) {
        AwardSaveReqVO.AwardItemVO vo = new AwardSaveReqVO.AwardItemVO();
        vo.setSkuId(SKU_ID);
        vo.setSupplierId(SUPPLIER_A);
        vo.setPrice(new BigDecimal(price));
        vo.setQty(new BigDecimal("10"));
        AwardSaveReqVO req = new AwardSaveReqVO();
        req.setInquiryId(null);
        req.setDeptId(DEPT_ID);
        req.setSubjectId(SUBJECT_ID);
        req.setItems(List.of(vo));
        return req;
    }

    private AwardItem awardItem() {
        AwardItem item = new AwardItem();
        item.setAwardId(AWARD_ID);
        item.setSkuId(SKU_ID);
        item.setSupplierId(SUPPLIER_A);
        item.setPrice(new BigDecimal("88"));
        item.setQty(new BigDecimal("10"));
        item.setQtyInBaseUnit(new BigDecimal("10"));
        return item;
    }

    /** ① D9：线下定标提交即占——AWARD 占用落 dept×subject 锚点，金额=定标金额。 */
    @Test
    void offlineAward_submit_occupiesOnAnchor() {
        when(awardItemMapper.selectList(any())).thenReturn(List.of(awardItem()));
        when(budgetOccupyService.occupiedTotal(BudgetBizType.AWARD, AWARD_ID)).thenReturn(BigDecimal.ZERO);
        when(budgetOccupyService.occupy(any(BudgetOccupyCmd.class)))
                .thenReturn(OccupyResultVO.ok(AMOUNT_V1, List.of()));

        awardService.submit(AWARD_ID);

        ArgumentCaptor<BudgetOccupyCmd> captor = ArgumentCaptor.forClass(BudgetOccupyCmd.class);
        verify(budgetOccupyService).occupy(captor.capture());
        BudgetOccupyCmd cmd = captor.getValue();
        assertEquals(BudgetBizType.AWARD, cmd.getBizType());
        assertEquals(AWARD_ID, cmd.getBizId());
        assertEquals(DEPT_ID, cmd.getDeptId());
        assertEquals(SUBJECT_ID, cmd.getSubjectId());
        assertEquals(0, cmd.getAmount().compareTo(AMOUNT_V1), "占用金额=当前定标金额");
    }

    /** ② P2b-3：AWARD 审批驳回 → 释放全部占用（RELEASE 负向流水，消除假占用）。 */
    @Test
    void awardRejected_releasesOccupation() {
        when(awardItemMapper.selectList(any())).thenReturn(List.of(awardItem()));
        // 提交时无历史占用；驳回时已有 10560 占用
        when(budgetOccupyService.occupiedTotal(BudgetBizType.AWARD, AWARD_ID))
                .thenReturn(BigDecimal.ZERO, AMOUNT_V1);
        awardService.submit(AWARD_ID);
        awardService.onRejected(1L, AWARD_ID, "不合格");

        assertEquals(AwardStatus.REJECTED, award.getStatus());
        ArgumentCaptor<BudgetOccupyCmd> captor = ArgumentCaptor.forClass(BudgetOccupyCmd.class);
        verify(budgetOccupyService).release(captor.capture());
        BudgetOccupyCmd release = captor.getValue();
        assertEquals(BudgetBizType.AWARD, release.getBizType());
        assertEquals(AWARD_ID, release.getBizId());
        assertEquals(0, release.getAmount().compareTo(AMOUNT_V1), "释放金额=已占用全额（守恒回冲）");
    }

    /**
     * ② P2b-4：驳回后调整重提 → <b>终态金额</b>断言——used 终态 = 当前有效定标金额 5280，
     * 而非 10560+5280=15840（覆盖式幂等：重提先释放旧占用再按新金额占用）。
     */
    @Test
    void awardResubmit_finalAmountNotDoubled() {
        when(awardItemMapper.selectList(any())).thenReturn(List.of(awardItem()));
        // ①首提（无历史占用→占 10560）→ ②驳回（occupiedTotal=10560→释放）→ ③重提（已释放→占 5280）
        when(budgetOccupyService.occupiedTotal(BudgetBizType.AWARD, AWARD_ID))
                .thenReturn(BigDecimal.ZERO, AMOUNT_V1, BigDecimal.ZERO);
        awardService.submit(AWARD_ID);
        awardService.onRejected(1L, AWARD_ID, "驳回");
        awardService.updateAwardItems(AWARD_ID, offlineReq("528"));
        awardService.submit(AWARD_ID);

        // 终态断言：两次 OCCUPY 分别 10560、5280（不存在 10560+5280 双占）
        ArgumentCaptor<BudgetOccupyCmd> occupyCaptor = ArgumentCaptor.forClass(BudgetOccupyCmd.class);
        verify(budgetOccupyService, times(2)).occupy(occupyCaptor.capture());
        List<BigDecimal> occupiedAmounts = occupyCaptor.getAllValues().stream()
                .map(BudgetOccupyCmd::getAmount).toList();
        assertEquals(0, occupiedAmounts.get(0).compareTo(AMOUNT_V1));
        assertEquals(0, occupiedAmounts.get(1).compareTo(AMOUNT_V2),
                "重提终态占用=当前有效定标金额（覆盖式幂等）");
        // 终态 used 模拟：Σoccupy − Σrelease == 5280（非 15840）
        BigDecimal releases = Mockito.mockingDetails(budgetOccupyService).getInvocations().stream()
                .filter(inv -> "release".equals(inv.getMethod().getName()))
                .map(inv -> ((BudgetOccupyCmd) inv.getArgument(0)).getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal finalUsed = occupiedAmounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add).subtract(releases);
        assertEquals(0, finalUsed.compareTo(AMOUNT_V2), "终态 used == 当前有效定标金额（非叠加）");
    }

    /** ③ P2b-3：作废端点——释放全部占用 + VOIDED 留痕。 */
    @Test
    void voidAward_releasesAndMarksVoided() {
        when(contractMapper.selectCount(any())).thenReturn(0L);
        when(budgetOccupyService.occupiedTotal(BudgetBizType.AWARD, AWARD_ID)).thenReturn(AMOUNT_V1);

        awardService.voidAward(AWARD_ID, "重复登记");

        assertEquals(AwardStatus.VOIDED, award.getStatus());
        ArgumentCaptor<BudgetOccupyCmd> captor = ArgumentCaptor.forClass(BudgetOccupyCmd.class);
        verify(budgetOccupyService).release(captor.capture());
        assertEquals(0, captor.getValue().getAmount().compareTo(AMOUNT_V1));
        assertTrue(award.getRemark().contains("作废"), "作废原因留痕");
    }

    /** ③ P2b-3：已登记合同的定标不可作废（合同链持有预算锚点，避免悬空）。 */
    @Test
    void voidAward_withContract_rejected() {
        when(contractMapper.selectCount(any())).thenReturn(1L);
        BizException ex = assertThrows(BizException.class, () -> awardService.voidAward(AWARD_ID, "x"));
        assertTrue(ex.getMessage().contains("不可作废"), ex.getMessage());
        verify(budgetOccupyService, never()).release(any(BudgetOccupyCmd.class));
    }

    /**
     * ④ P2b-5/6：无申请来源订单变更超额 → 拒绝（不再静默绕过）+ BUDGET 升级任务
     * （bizId=订单 id，payload 含 subjectId 锚点）。
     */
    @Test
    void changeOrder_noApply_overBudget_blockedAndEscalated() {
        PurchaseOrder order = order();
        when(purchaseOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(contractMapper.selectForUpdate(CONTRACT_ID)).thenReturn(contract());
        // 锚点回填链②：contract.award_id → award.dept/subject
        when(contractMapper.selectById(CONTRACT_ID)).thenReturn(contract());
        when(contractMapper.deductAvailable(eq(CONTRACT_ID), any(BigDecimal.class), any(Integer.class)))
                .thenReturn(1);
        when(awardMapper.selectById(9L)).thenReturn(anchorAward());
        when(orderItemMapper.selectList(any())).thenReturn(List.of(orderItem()));
        // 预算不足 → 拦截
        when(budgetOccupyService.occupy(any(BudgetOccupyCmd.class)))
                .thenReturn(OccupyResultVO.blocked(new BigDecimal("0"), new BigDecimal("50688"), "当月预算余额不足"));

        OrderChangeReqVO req = new OrderChangeReqVO();
        req.setReason("加量");
        OrderChangeReqVO.ItemChange change = new OrderChangeReqVO.ItemChange();
        change.setOrderItemId(ORDER_ITEM_ID);
        change.setNewQty(new BigDecimal("50"));
        req.setItems(List.of(change));

        BizException ex = assertThrows(BizException.class, () -> orderService.changeOrder(ORDER_ID, req));
        assertTrue(ex.getMessage().contains("预算"), ex.getMessage());

        // 升级任务断言：bizType=BUDGET、bizId=订单 id（QA2-06）、payload 带 subjectId
        ArgumentCaptor<ApprovalTaskSpec> specCaptor = ArgumentCaptor.forClass(ApprovalTaskSpec.class);
        verify(approvalGateway).create(specCaptor.capture());
        ApprovalTaskSpec spec = specCaptor.getValue();
        assertEquals("BUDGET", spec.getBizType());
        assertEquals(ORDER_ID, spec.getBizId());
        assertTrue(spec.getPayloadJson().contains("\"subjectId\":" + SUBJECT_ID), spec.getPayloadJson());
        // 占用 cmd 锚点断言（dept×subject 回填链生效）
        ArgumentCaptor<BudgetOccupyCmd> occupyCaptor = ArgumentCaptor.forClass(BudgetOccupyCmd.class);
        verify(budgetOccupyService).occupy(occupyCaptor.capture());
        assertEquals(DEPT_ID, occupyCaptor.getValue().getDeptId());
        assertEquals(SUBJECT_ID, occupyCaptor.getValue().getSubjectId());
    }

    /** ⑤ P2b-10：无申请来源且合同未关联定标 → 变更增额硬控拦截（真实链路：occupied=0 也必过锚点解析）。 */
    @Test
    void changeOrder_noAnchor_hardBlocked() {
        PurchaseOrder order = order();
        // P2b-6 后无锚订单 budget_occupied=0——真实链路形态（不再手工 mock occupied>0）
        order.setBudgetOccupied(BigDecimal.ZERO);
        when(purchaseOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        Contract noAnchor = contract();
        noAnchor.setAwardId(null);
        when(contractMapper.selectForUpdate(CONTRACT_ID)).thenReturn(noAnchor);
        when(contractMapper.selectById(CONTRACT_ID)).thenReturn(noAnchor);
        when(contractMapper.deductAvailable(eq(CONTRACT_ID), any(BigDecimal.class), any(Integer.class)))
                .thenReturn(1);
        when(orderItemMapper.selectList(any())).thenReturn(List.of(orderItem()));

        OrderChangeReqVO req = new OrderChangeReqVO();
        req.setReason("加量");
        OrderChangeReqVO.ItemChange change = new OrderChangeReqVO.ItemChange();
        change.setOrderItemId(ORDER_ITEM_ID);
        change.setNewQty(new BigDecimal("50"));
        req.setItems(List.of(change));

        BizException ex = assertThrows(BizException.class, () -> orderService.changeOrder(ORDER_ID, req));
        assertTrue(ex.getMessage().contains("无预算锚点"), ex.getMessage());
        verify(budgetOccupyService, never()).occupy(any(BudgetOccupyCmd.class));
        verify(approvalGateway, never()).create(any(ApprovalTaskSpec.class));
    }

    /** P2b-6：无申请来源下单 → AWARD→ORDER 同行转移落 ORDER 流水。 */
    @Test
    void createOrder_noApply_transfersAwardToOrder() {
        Contract contract = contract();
        when(contractMapper.selectForUpdate(CONTRACT_ID)).thenReturn(contract);
        when(contractMapper.selectById(CONTRACT_ID)).thenReturn(contract);
        when(contractMapper.deductAvailable(eq(CONTRACT_ID), any(BigDecimal.class), any(Integer.class)))
                .thenReturn(1);
        when(contractMapper.updateById(any(Contract.class))).thenReturn(1);
        when(purchaseOrderMapper.insert(any(PurchaseOrder.class))).thenReturn(1);
        when(unitConversionMapper.selectCurrentEffective(anyLong(), anyString(), any())).thenReturn(null);
        when(budgetOccupyService.transfer(any(com.dzgylxt.vo.budget.BudgetTransferCmd.class)))
                .thenAnswer(inv -> OccupyResultVO.ok(inv.getArgument(0, com.dzgylxt.vo.budget.BudgetTransferCmd.class)
                        .getAmount(), List.of()));

        com.dzgylxt.vo.order.OrderCreateReqVO req = new com.dzgylxt.vo.order.OrderCreateReqVO();
        req.setApplyId(null);
        req.setContractId(CONTRACT_ID);
        req.setRemark("D9 下单");
        com.dzgylxt.vo.order.OrderCreateReqVO.OrderItemReqVO itemReq = new com.dzgylxt.vo.order.OrderCreateReqVO.OrderItemReqVO();
        itemReq.setSkuId(SKU_ID);
        itemReq.setQty(new BigDecimal("20"));
        itemReq.setPrice(new BigDecimal("88"));
        itemReq.setPurchaseUnit("BOX");
        req.setItems(List.of(itemReq));

        orderService.createOrder(req);

        ArgumentCaptor<com.dzgylxt.vo.budget.BudgetTransferCmd> captor =
                ArgumentCaptor.forClass(com.dzgylxt.vo.budget.BudgetTransferCmd.class);
        verify(budgetOccupyService).transfer(captor.capture());
        com.dzgylxt.vo.budget.BudgetTransferCmd transfer = captor.getValue();
        assertEquals(BudgetBizType.AWARD, transfer.getFromBizType());
        assertEquals(9L, transfer.getFromBizId());
        assertEquals(BudgetBizType.ORDER, transfer.getToBizType());
        assertEquals(0, transfer.getAmount().compareTo(new BigDecimal("1760")), "转移金额=订单金额");
    }

    // ---------------- 夹具 ----------------

    private PurchaseOrder order() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(ORDER_ID);
        order.setOrderNo("DD-TEST-000001");
        order.setContractId(CONTRACT_ID);
        order.setApplyId(null);
        order.setStatus(OrderStatus.CREATED);
        order.setBudgetOccupied(new BigDecimal("2112"));
        return order;
    }

    private Contract contract() {
        Contract contract = new Contract();
        contract.setId(CONTRACT_ID);
        contract.setNo("HT-TEST-000001");
        contract.setAwardId(9L);
        contract.setStatus(ContractStatus.EFFECTIVE);
        contract.setValidFrom(LocalDate.now().minusDays(1));
        contract.setValidTo(LocalDate.now().plusDays(365));
        contract.setAmount(new BigDecimal("100000"));
        contract.setAvailableAmount(new BigDecimal("95000"));
        contract.setVersion(0);
        return contract;
    }

    /** D9 锚点定标（合同关联的 award，dept×subject 已登记、提交即占）。 */
    private com.dzgylxt.entity.purchase.Award anchorAward() {
        com.dzgylxt.entity.purchase.Award anchor = new com.dzgylxt.entity.purchase.Award();
        anchor.setId(9L);
        anchor.setAwardNo("DB-TEST-000009");
        anchor.setDeptId(DEPT_ID);
        anchor.setSubjectId(SUBJECT_ID);
        anchor.setAmount(AMOUNT_V1);
        anchor.setStatus(AwardStatus.APPROVED);
        return anchor;
    }

    private OrderItem orderItem() {
        OrderItem item = new OrderItem();
        item.setId(ORDER_ITEM_ID);
        item.setOrderId(ORDER_ID);
        item.setSkuId(SKU_ID);
        item.setQtyPurchase(new BigDecimal("2"));
        item.setQtyBase(new BigDecimal("20"));
        item.setPrice(new BigDecimal("88"));
        item.setApplyItemId(null);
        return item;
    }
}
