package com.dzgylxt.service.impl.settlement;

import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.common.BizException;
import com.dzgylxt.entity.order.Arrival;
import com.dzgylxt.entity.order.ArrivalItem;
import com.dzgylxt.entity.order.OrderItem;
import com.dzgylxt.entity.order.PurchaseOrder;
import com.dzgylxt.entity.settlement.Settlement;
import com.dzgylxt.enums.ItemType;
import com.dzgylxt.enums.OrderStatus;
import com.dzgylxt.enums.SettlementStatus;
import com.dzgylxt.enums.SettleMode;
import com.dzgylxt.enums.SettlementType;
import com.dzgylxt.mapper.order.ArrivalItemMapper;
import com.dzgylxt.mapper.order.ArrivalMapper;
import com.dzgylxt.mapper.order.OrderItemMapper;
import com.dzgylxt.mapper.order.PurchaseOrderMapper;
import com.dzgylxt.mapper.order.ServiceAssessMapper;
import com.dzgylxt.mapper.settlement.SettlementMapper;
import com.dzgylxt.mapper.settlement.PaymentMapper;
import com.dzgylxt.service.IBudgetOccupyService;
import com.dzgylxt.vo.settlement.SettlementSaveReqVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P3b-T02 D10 预付款结算单测（R4）+ QA P1-1 复现即回归。
 *
 * <p>覆盖：预付款从订单发起（无到货、paymentStage=1、type=PREPAYMENT）、累计不超过订单有效金额；
 * 在途（PENDING）预付款计入承诺口径（QA P1-1：3 笔连发第 3 笔拦截 / 在途计入尾款扣减 / 审批时点复算兜底 /
 * PUT 改额同口径封顶）；尾款结算自动扣减预付款承诺（prepaymentDeduction 回填、paymentStage=3）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettlementPrepaymentTest {

    private static final long ORDER_ID = 3001L;
    private static final long PREPAY_ID = 4001L;

    @Mock
    private SettlementMapper settlementMapper;

    @Mock
    private PurchaseOrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private ArrivalMapper arrivalMapper;

    @Mock
    private ArrivalItemMapper arrivalItemMapper;

    @Mock
    private ServiceAssessMapper serviceAssessMapper;

    @Mock
    private IBudgetOccupyService budgetOccupyService;

    @Mock
    private BusinessNoGenerator businessNoGenerator;

    @Mock
    private org.springframework.beans.factory.ObjectProvider<com.dzgylxt.approval.ApprovalGateway> gatewayProvider;

    @Mock
    private PaymentMapper paymentMapper;

    private SettlementServiceImpl settlementService;

    @BeforeEach
    void setUp() {
        settlementService = new SettlementServiceImpl(gatewayProvider);
        ReflectionTestUtils.setField(settlementService, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(settlementService, "orderItemMapper", orderItemMapper);
        ReflectionTestUtils.setField(settlementService, "arrivalMapper", arrivalMapper);
        ReflectionTestUtils.setField(settlementService, "arrivalItemMapper", arrivalItemMapper);
        ReflectionTestUtils.setField(settlementService, "serviceAssessMapper", serviceAssessMapper);
        ReflectionTestUtils.setField(settlementService, "baseMapper", settlementMapper);
        ReflectionTestUtils.setField(settlementService, "businessNoGenerator", businessNoGenerator);
        ReflectionTestUtils.setField(settlementService, "paymentMapper", paymentMapper);
        ReflectionTestUtils.setField(settlementService, "budgetOccupyService", budgetOccupyService);

        when(businessNoGenerator.nextNo(any())).thenReturn("JS-202609-000001");
        lenient().when(settlementMapper.insert(any(Settlement.class))).thenReturn(1);
        lenient().when(settlementMapper.updateById(any(Settlement.class))).thenReturn(1);
        lenient().when(settlementMapper.existsByArrival(any())).thenReturn(false);
        // 订单应结总额固定 1200（orderTotalAmount 口径；预付款累计校验 / 尾款扣减依赖）
        lenient().when(orderItemMapper.selectList(any())).thenReturn(List.of(item()));
        // 物料单无考核扣款 → totalDeductOf 返回空列表（避免 NPE）
        lenient().when(serviceAssessMapper.selectList(any())).thenReturn(List.of());
        // 入库合格累计 = 12（满足结算数量校验）
        lenient().when(arrivalMapper.selectList(any())).thenReturn(List.of(arrival()));
        lenient().when(arrivalItemMapper.selectList(any())).thenReturn(List.of(arrivalItem(new BigDecimal("12"))));
    }

    /** 预付款结算：订单发起、type=PREPAYMENT、paymentStage=1、无到货、PENDING。 */
    @Test
    void createPrepaymentSettlement_marksPrepaymentAndStage1() {
        PurchaseOrder order = order(OrderStatus.CREATED);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(settlementMapper.sumPrepaymentCommitted(ORDER_ID)).thenReturn(BigDecimal.ZERO);

        Long id = settlementService.createPrepaymentSettlement(ORDER_ID, new BigDecimal("300"), "预付款");

        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementMapper).insert(captor.capture());
        Settlement s = captor.getValue();
        assertEquals(SettlementType.PREPAYMENT, s.getType(), "预付款类型");
        assertEquals(Integer.valueOf(1), s.getPaymentStage(), "付款阶段=1");
        assertEquals(0, s.getAmount().compareTo(new BigDecimal("300")));
        assertEquals(Integer.valueOf(0), s.getIsFinal());
        assertNull(s.getArrivalId(), "预付款无到货");
        assertEquals(SettlementStatus.PENDING, s.getStatus());
    }

    /** 预付款累计（committed 口径：PENDING+SETTLED 在途计入）不得超过订单有效金额（应结总额）。 */
    @Test
    void createPrepaymentSettlement_capsAtOrderValidAmount() {
        PurchaseOrder order = order(OrderStatus.CREATED);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item())); // 应结 1200
        when(settlementMapper.sumPrepaymentCommitted(ORDER_ID)).thenReturn(new BigDecimal("1000"));

        // 已承诺（含在途）1000 + 本次 300 > 1200 → 拒绝
        assertThrows(BizException.class,
                () -> settlementService.createPrepaymentSettlement(ORDER_ID, new BigDecimal("300"), "超额预付款"));
    }

    /** QA P1-1 复现即回归（QA 攻击路径）：订单 1200 连发 3 笔在途预付款 1000/200/100，第 3 笔必须被拦。 */
    @Test
    void createPrepaymentSettlement_inFlightCounted_thirdBlocked() {
        PurchaseOrder order = order(OrderStatus.CREATED);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);
        // 前两笔在途预付款 1000+200 = 1200 已达 cap（sumPrepaymentCommitted：PENDING 计入）
        when(settlementMapper.sumPrepaymentCommitted(ORDER_ID)).thenReturn(new BigDecimal("1200"));

        assertThrows(BizException.class,
                () -> settlementService.createPrepaymentSettlement(ORDER_ID, new BigDecimal("100"), "第3笔在途超额"));
        verify(settlementMapper, never()).insert(any(Settlement.class));
    }

    /** QA P1-1：在途预付款存在时创建尾款——扣减额与结清校验按 committed 口径含在途；paymentStage=3（P3-2）。 */
    @Test
    void finalSettlement_deductionIncludesInFlightPrepayment() {
        PurchaseOrder order = order(OrderStatus.RECEIVED);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(settlementMapper.sumPrepaymentCommitted(ORDER_ID)).thenReturn(new BigDecimal("200")); // 全部 PENDING 在途
        when(settlementMapper.selectByOrder(ORDER_ID)).thenReturn(List.of()); // 无历史已结

        SettlementSaveReqVO req = new SettlementSaveReqVO();
        req.setOrderId(ORDER_ID);
        req.setSettledQtyBase(new BigDecimal("12"));
        req.setSettleMode(SettleMode.FINAL);
        req.setIsFinal(1);

        settlementService.createSettlement(req);

        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementMapper).insert(captor.capture());
        Settlement s = captor.getValue();
        assertEquals(0, s.getAmount().compareTo(new BigDecimal("1000")),
                "尾款金额 = 1200 − 200(在途预付款承诺) = 1000");
        assertEquals(0, s.getPrepaymentDeduction().compareTo(new BigDecimal("200")),
                "扣减额按 committed 口径含在途");
        assertEquals(Integer.valueOf(3), s.getPaymentStage(), "P3-2：尾款 paymentStage=3");
    }

    /** QA P1-1 审批兜底：SETTLEMENT 通过时点复算 Σ已批预付款 + 本单 ≤ 订单有效金额，超额抛出且不结清。 */
    @Test
    void approval_recomputesPrepaymentCap_andBlocks() {
        PurchaseOrder order = order(OrderStatus.RECEIVED);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);
        Settlement prepayment = prepayment(SettlementStatus.PENDING, new BigDecimal("300"));
        when(settlementMapper.selectById(PREPAY_ID)).thenReturn(prepayment);
        // 已批 1000 + 本单 300 = 1300 > 订单有效金额 1200（orderItemMapper 已在 setUp 桩为 1200）
        when(settlementMapper.sumPrepaymentPaid(ORDER_ID)).thenReturn(new BigDecimal("1000"));

        assertThrows(BizException.class,
                () -> settlementService.handleApproval(9001L, PREPAY_ID, true, "ok"));
        assertEquals(SettlementStatus.PENDING, prepayment.getStatus(), "审批拦截：不结清、保持 PENDING");
        verify(budgetOccupyService, never()).writeOff(any(com.dzgylxt.vo.budget.BudgetOccupyCmd.class));
    }

    /** QA P1-1：PUT /{id} 修改预付款金额同口径封顶——Σ其他预付款(含在途，不含本单) + 本次 ≤ 订单有效金额。 */
    @Test
    void updatePrepayment_amountCappedAtOrderValidAmount() {
        Settlement prepayment = prepayment(SettlementStatus.PENDING, new BigDecimal("1000"));
        when(settlementMapper.selectById(PREPAY_ID)).thenReturn(prepayment);
        // committed = 1000（含本单 1000）；剔除本单后其他预付款 = 0，改 1300 > 1200 → 拦截
        when(settlementMapper.sumPrepaymentCommitted(ORDER_ID)).thenReturn(new BigDecimal("1000"));

        SettlementSaveReqVO req = new SettlementSaveReqVO();
        req.setAmount(new BigDecimal("1300"));
        assertThrows(BizException.class, () -> settlementService.updateSettlement(PREPAY_ID, req));
        assertEquals(0, prepayment.getAmount().compareTo(new BigDecimal("1000")), "拦截后金额不变");
    }

    /** 尾款结算自动扣减已批预付款：金额 = 应结总额 − 预付款；prepaymentDeduction 回填。 */
    @Test
    void finalSettlement_autoDeductsPrepayment() {
        PurchaseOrder order = order(OrderStatus.RECEIVED);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item())); // 应结 1200
        when(settlementMapper.sumPrepaymentCommitted(ORDER_ID)).thenReturn(new BigDecimal("300"));
        when(settlementMapper.selectByOrder(ORDER_ID)).thenReturn(List.of()); // 无历史结算

        SettlementSaveReqVO req = new SettlementSaveReqVO();
        req.setOrderId(ORDER_ID);
        req.setSettledQtyBase(new BigDecimal("12"));
        req.setSettleMode(SettleMode.FINAL);
        req.setIsFinal(1);
        // 不显式给金额 → 应自动 = 1200 − 300 = 900

        settlementService.createSettlement(req);

        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementMapper).insert(captor.capture());
        Settlement s = captor.getValue();
        assertEquals(0, s.getAmount().compareTo(new BigDecimal("900")), "尾款自动扣减预付款：1200-300=900");
        assertEquals(0, s.getPrepaymentDeduction().compareTo(new BigDecimal("300")));
        assertEquals(Integer.valueOf(1), s.getIsFinal());
    }

    // ---------------- fixtures ----------------

    private Settlement prepayment(SettlementStatus status, BigDecimal amount) {
        Settlement s = new Settlement();
        s.setId(PREPAY_ID);
        s.setOrderId(ORDER_ID);
        s.setSettleNo("JS-202609-000002");
        s.setAmount(amount);
        s.setType(SettlementType.PREPAYMENT);
        s.setIsFinal(0);
        s.setPaymentStage(1);
        s.setStatus(status);
        return s;
    }

    private Arrival arrival() {
        Arrival a = new Arrival();
        a.setId(5001L);
        a.setOrderId(ORDER_ID);
        return a;
    }

    private ArrivalItem arrivalItem(BigDecimal qty) {
        ArrivalItem i = new ArrivalItem();
        i.setArrivalId(5001L);
        i.setQtyStored(qty);
        return i;
    }

    private OrderItem item() {
        OrderItem i = new OrderItem();
        i.setId(4001L);
        i.setOrderId(ORDER_ID);
        i.setQtyBase(new BigDecimal("12"));
        i.setPrice(new BigDecimal("100"));
        return i;
    }

    private PurchaseOrder order(OrderStatus status) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(ORDER_ID);
        order.setOrderNo("DD-202609-000001");
        order.setSupplierId(2102245025428721666L);
        order.setStatus(status);
        order.setOrderType(ItemType.MATERIAL);
        return order;
    }

    // ---------------- B9：作废结算单 ----------------

    @Test
    void voidSettlement_pending_success() {
        Settlement s = new Settlement();
        s.setId(5001L);
        s.setOrderId(ORDER_ID);
        s.setStatus(SettlementStatus.PENDING);
        s.setType(SettlementType.PREPAYMENT);
        s.setAmount(new BigDecimal("500"));
        s.setRemark("预付款");
        when(settlementMapper.selectById(5001L)).thenReturn(s);
        when(paymentMapper.sumPaidAmount(5001L)).thenReturn(BigDecimal.ZERO);
        when(settlementMapper.updateById(any(Settlement.class))).thenReturn(1);

        settlementService.voidSettlement(5001L, "误建");

        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementMapper).updateById(captor.capture());
        assertEquals(SettlementStatus.VOIDED, captor.getValue().getStatus());
        assertEquals("预付款；作废：误建", captor.getValue().getRemark());
    }

    @Test
    void voidSettlement_settled_rejected() {
        Settlement s = new Settlement();
        s.setId(5002L);
        s.setOrderId(ORDER_ID);
        s.setStatus(SettlementStatus.SETTLED);
        when(settlementMapper.selectById(5002L)).thenReturn(s);

        BizException ex = assertThrows(BizException.class,
                () -> settlementService.voidSettlement(5002L, "test"));
        assertEquals(com.dzgylxt.common.ResultCode.STATUS_INVALID.getCode(), ex.getCode());
    }

    @Test
    void voidSettlement_alreadyVoided_idempotent() {
        Settlement s = new Settlement();
        s.setId(5003L);
        s.setOrderId(ORDER_ID);
        s.setStatus(SettlementStatus.VOIDED);
        when(settlementMapper.selectById(5003L)).thenReturn(s);

        BizException ex = assertThrows(BizException.class,
                () -> settlementService.voidSettlement(5003L, "重复"));
        assertEquals(com.dzgylxt.common.ResultCode.STATUS_INVALID.getCode(), ex.getCode());
    }

    @Test
    void voidSettlement_hasPayment_rejected() {
        Settlement s = new Settlement();
        s.setId(5004L);
        s.setOrderId(ORDER_ID);
        s.setStatus(SettlementStatus.PENDING);
        s.setType(SettlementType.MATERIAL);
        s.setAmount(new BigDecimal("2000"));
        when(settlementMapper.selectById(5004L)).thenReturn(s);
        when(paymentMapper.sumPaidAmount(5004L)).thenReturn(new BigDecimal("500"));

        BizException ex = assertThrows(BizException.class,
                () -> settlementService.voidSettlement(5004L, "test"));
        assertEquals(com.dzgylxt.common.ResultCode.STATUS_INVALID.getCode(), ex.getCode());
    }
}
