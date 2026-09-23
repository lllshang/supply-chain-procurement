package com.dzgylxt.service.impl.settlement;

import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.entity.order.OrderItem;
import com.dzgylxt.entity.order.PurchaseOrder;
import com.dzgylxt.entity.settlement.Payment;
import com.dzgylxt.entity.settlement.Settlement;
import com.dzgylxt.enums.BudgetBizType;
import com.dzgylxt.enums.ItemType;
import com.dzgylxt.enums.OrderStatus;
import com.dzgylxt.enums.PaymentStatus;
import com.dzgylxt.enums.SettlementStatus;
import com.dzgylxt.mapper.order.OrderItemMapper;
import com.dzgylxt.mapper.order.PurchaseOrderMapper;
import com.dzgylxt.mapper.order.ServiceAssessMapper;
import com.dzgylxt.mapper.settlement.PaymentMapper;
import com.dzgylxt.mapper.settlement.SettlementMapper;
import com.dzgylxt.service.IBudgetOccupyService;
import com.dzgylxt.vo.budget.BudgetOccupyCmd;
import com.dzgylxt.vo.settlement.PaymentSaveReqVO;
import com.dzgylxt.vo.settlement.StatementVO;
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
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P3-T04/T05 结算/付款状态机与预算核销联动单测。
 *
 * <p>覆盖：结算审批通过→核销（writeOff ORDER+金额）+ 订单 SETTLED；付款仅 SETTLED
 * 可发起；累计付款 ≤ 结算金额；登记确认→PAID→订单全部付清→PAID；对账单守恒。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettlementPaymentTest {

    private static final long ORDER_ID = 3001L;
    private static final long SETTLE_ID = 3101L;
    private static final long PAY_ID = 3201L;

    @Mock
    private SettlementMapper settlementMapper;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private PurchaseOrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    /** #42/#43 新增依赖：totalDeductOf 经此查 Σ考核扣款（物料单应为空列表 → 0）。 */
    @Mock
    private ServiceAssessMapper serviceAssessMapper;

    @Mock
    private IBudgetOccupyService budgetOccupyService;

    @Mock
    private BusinessNoGenerator businessNoGenerator;

    @Mock
    private org.springframework.beans.factory.ObjectProvider<com.dzgylxt.approval.ApprovalGateway> gatewayProvider;

    private SettlementServiceImpl settlementService;
    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        settlementService = new SettlementServiceImpl(gatewayProvider);
        ReflectionTestUtils.setField(settlementService, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(settlementService, "orderItemMapper", orderItemMapper);
        ReflectionTestUtils.setField(settlementService, "serviceAssessMapper", serviceAssessMapper);
        ReflectionTestUtils.setField(settlementService, "baseMapper", settlementMapper);
        ReflectionTestUtils.setField(settlementService, "budgetOccupyService", budgetOccupyService);

        paymentService = new PaymentServiceImpl(gatewayProvider);
        ReflectionTestUtils.setField(paymentService, "settlementMapper", settlementMapper);
        ReflectionTestUtils.setField(paymentService, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(paymentService, "orderItemMapper", orderItemMapper);
        ReflectionTestUtils.setField(paymentService, "businessNoGenerator", businessNoGenerator);
        ReflectionTestUtils.setField(paymentService, "baseMapper", paymentMapper);

        when(businessNoGenerator.nextNo(any())).thenReturn("FK-202609-000001");
        org.mockito.Mockito.lenient().when(settlementMapper.updateById(any(Settlement.class))).thenReturn(1);
        org.mockito.Mockito.lenient().when(paymentMapper.updateById(any(Payment.class))).thenReturn(1);
        org.mockito.Mockito.lenient().when(orderMapper.updateById(any(PurchaseOrder.class))).thenReturn(1);
    }

    // ---------------- T04：结算核销联动 ----------------

    /** 结算审批通过 → writeOff(ORDER, 金额) + 订单全部结清 → SETTLED。 */
    @Test
    void settlementApproved_writesOffBudget_andSettlesOrder() {
        stubOrderTotal(new BigDecimal("1200"));
        when(settlementMapper.selectById(SETTLE_ID)).thenReturn(settlement(SettlementStatus.PENDING));
        when(settlementMapper.selectByOrder(ORDER_ID))
                .thenReturn(List.of(settlement(SettlementStatus.SETTLED)));
        when(settlementMapper.sumSettledAmount(ORDER_ID)).thenReturn(new BigDecimal("1200"));
        PurchaseOrder order = order(OrderStatus.RECEIVED);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);

        settlementService.handleApproval(9001L, SETTLE_ID, true, "ok");

        // 核销命令：bizType=ORDER、bizId=订单、金额=结算金额
        ArgumentCaptor<BudgetOccupyCmd> cmd = ArgumentCaptor.forClass(BudgetOccupyCmd.class);
        verify(budgetOccupyService).writeOff(cmd.capture());
        assertEquals(BudgetBizType.ORDER, cmd.getValue().getBizType());
        assertEquals(ORDER_ID, cmd.getValue().getBizId());
        assertEquals(0, cmd.getValue().getAmount().compareTo(new BigDecimal("1200")));
        // 订单结清收口
        assertEquals(OrderStatus.SETTLED, order.getStatus());
        verify(orderMapper).updateById(order);
    }

    /** 结算审批驳回 → 保持 PENDING（无核销、无状态变更）。 */
    @Test
    void settlementRejected_staysPending() {
        stubOrderTotal(new BigDecimal("1200"));
        Settlement s = settlement(SettlementStatus.PENDING);
        when(settlementMapper.selectById(SETTLE_ID)).thenReturn(s);

        settlementService.handleApproval(9002L, SETTLE_ID, false, "材料不符");

        assertEquals(SettlementStatus.PENDING, s.getStatus(), "驳回保持 PENDING 留痕可重提");
        verify(budgetOccupyService, org.mockito.Mockito.never())
                .writeOff(any(BudgetOccupyCmd.class));
    }

    // ---------------- T05：付款两阶段 ----------------

    /** 付款创建：仅 SETTLED 可发起 + 累计付款 ≤ 结算金额。 */
    @Test
    void createPayment_requiresSettled_andCapsAtAmount() {
        Settlement settled = settlement(SettlementStatus.SETTLED);
        when(settlementMapper.selectById(SETTLE_ID)).thenReturn(settled);
        // #39 后封顶口径改为"累计付款（含在途）"：sumCommittedAmount 已含已付+在途
        when(paymentMapper.sumCommittedAmount(SETTLE_ID)).thenReturn(new BigDecimal("1000"));

        // 已承诺 1000 + 本次 300 > 结算 1200 → 拒绝
        PaymentSaveReqVO over = new PaymentSaveReqVO();
        over.setSettlementId(SETTLE_ID);
        over.setPayAmount(new BigDecimal("300"));
        assertThrows(com.dzgylxt.common.BizException.class, () -> paymentService.createPayment(over));

        // 本次 200 → 通过
        over.setPayAmount(new BigDecimal("200"));
        paymentService.createPayment(over);
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentMapper).insert(captor.capture());
        assertEquals("FK-202609-000001", captor.getValue().getPayNo());
        assertEquals(PaymentStatus.UNPAID, captor.getValue().getStatus());
    }

    /** 登记确认 → PAID + 结算单全部付清 → 订单 PAID（付款确认无预算动作）。 */
    @Test
    void confirmPayment_marksPaid_andCompletesOrder() {
        Settlement settled = settlement(SettlementStatus.SETTLED);
        Payment payment = payment(PaymentStatus.UNPAID);
        when(paymentMapper.selectById(PAY_ID)).thenReturn(payment);
        when(settlementMapper.selectById(SETTLE_ID)).thenReturn(settled);
        when(settlementMapper.selectByOrder(ORDER_ID)).thenReturn(List.of(settled));
        when(paymentMapper.sumPaidAmount(SETTLE_ID)).thenReturn(new BigDecimal("1200"));
        PurchaseOrder order = order(OrderStatus.SETTLED);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item()));

        paymentService.confirmPayment(PAY_ID, "voucher-001.pdf", LocalDate.of(2026, 9, 24));

        assertEquals(PaymentStatus.PAID, payment.getStatus());
        assertEquals(OrderStatus.PAID, order.getStatus(), "全部付清订单 → PAID");
        verify(orderMapper).updateById(order);
        // 付款确认无预算动作（核销已在结算完成，规格 §5 行 12）
        verify(budgetOccupyService, org.mockito.Mockito.never()).writeOff(any());
        verify(budgetOccupyService, org.mockito.Mockito.never()).occupy(any());
    }

    /** 对账单：应付 − 已付 = 差额（守恒）。 */
    @Test
    void statement_balances() {
        Settlement settled = settlement(SettlementStatus.SETTLED);
        when(orderMapper.selectList(any())).thenReturn(List.of(order(OrderStatus.SETTLED)));
        when(settlementMapper.selectByOrder(ORDER_ID)).thenReturn(List.of(settled));
        Payment paid = payment(PaymentStatus.PAID);
        paid.setPayDate(LocalDate.of(2026, 9, 24));
        when(paymentMapper.selectBySettlement(SETTLE_ID)).thenReturn(List.of(paid));

        StatementVO vo = paymentService.statement(2102245025428721666L, null, null);

        assertEquals(0, vo.getTotalPayable().compareTo(new BigDecimal("1200")));
        assertEquals(0, vo.getTotalPaid().compareTo(new BigDecimal("1200")));
        assertEquals(0, vo.getTotalPayable().subtract(vo.getTotalPaid())
                .compareTo(vo.getBalance()), "应付−已付=差额守恒");
    }

    // ---------------- fixtures ----------------

    private void stubOrderTotal(BigDecimal total) {
        // 单明细：price×qtyBase = total
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item()));
        when(settlementMapper.selectByOrder(ORDER_ID)).thenReturn(List.of());
    }

    private Settlement settlement(SettlementStatus status) {
        Settlement s = new Settlement();
        s.setId(SETTLE_ID);
        s.setOrderId(ORDER_ID);
        s.setSettleNo("JS-202609-000001");
        s.setAmount(new BigDecimal("1200"));
        s.setSettledQtyBase(new BigDecimal("12"));
        s.setStatus(status);
        s.setCreatedAt(java.time.LocalDateTime.now());
        return s;
    }

    private Payment payment(PaymentStatus status) {
        Payment p = new Payment();
        p.setId(PAY_ID);
        p.setSettlementId(SETTLE_ID);
        p.setPayNo("FK-202609-000001");
        p.setPayAmount(new BigDecimal("1200"));
        p.setStatus(status);
        p.setReviewedAt(java.time.LocalDateTime.now());
        return p;
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
}
