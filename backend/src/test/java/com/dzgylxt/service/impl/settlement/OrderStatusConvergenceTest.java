package com.dzgylxt.service.impl.settlement;

import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.entity.order.OrderItem;
import com.dzgylxt.entity.order.PurchaseOrder;
import com.dzgylxt.entity.settlement.Payment;
import com.dzgylxt.entity.settlement.Settlement;
import com.dzgylxt.enums.ItemType;
import com.dzgylxt.enums.OrderStatus;
import com.dzgylxt.enums.PaymentStatus;
import com.dzgylxt.enums.SettlementStatus;
import com.dzgylxt.mapper.order.OrderItemMapper;
import com.dzgylxt.mapper.order.PurchaseOrderMapper;
import com.dzgylxt.mapper.settlement.PaymentMapper;
import com.dzgylxt.mapper.settlement.SettlementMapper;
import com.dzgylxt.service.IBudgetOccupyService;
import com.dzgylxt.service.impl.order.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P3b-T03 D11 订单状态机收敛单测（R5）。
 *
 * <p>覆盖：结算审批通过 / 付款登记确认均不写订单 SETTLED/PAID（结清/付清改派生字段）；
 * OrderServiceImpl.fillProgress 由结算/付款聚合计算 settleProgress/paidProgress。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderStatusConvergenceTest {

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

    @Mock
    private IBudgetOccupyService budgetOccupyService;

    @Mock
    private BusinessNoGenerator businessNoGenerator;

    @Mock
    private org.springframework.beans.factory.ObjectProvider<com.dzgylxt.approval.ApprovalGateway> gatewayProvider;

    private SettlementServiceImpl settlementService;
    private PaymentServiceImpl paymentService;
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        settlementService = new SettlementServiceImpl(gatewayProvider);
        ReflectionTestUtils.setField(settlementService, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(settlementService, "orderItemMapper", orderItemMapper);
        ReflectionTestUtils.setField(settlementService, "baseMapper", settlementMapper);
        ReflectionTestUtils.setField(settlementService, "budgetOccupyService", budgetOccupyService);

        // R6：付款免审批——PaymentServiceImpl 无审批网关依赖
        paymentService = new PaymentServiceImpl();
        ReflectionTestUtils.setField(paymentService, "settlementMapper", settlementMapper);
        ReflectionTestUtils.setField(paymentService, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(paymentService, "orderItemMapper", orderItemMapper);
        ReflectionTestUtils.setField(paymentService, "businessNoGenerator", businessNoGenerator);
        ReflectionTestUtils.setField(paymentService, "baseMapper", paymentMapper);

        orderService = new OrderServiceImpl();
        ReflectionTestUtils.setField(orderService, "settlementMapper", settlementMapper);
        ReflectionTestUtils.setField(orderService, "paymentMapper", paymentMapper);
        ReflectionTestUtils.setField(orderService, "orderItemMapper", orderItemMapper);

        when(businessNoGenerator.nextNo(any())).thenReturn("FK-202609-000001");
        lenient().when(settlementMapper.updateById(any(Settlement.class))).thenReturn(1);
        lenient().when(paymentMapper.updateById(any(Payment.class))).thenReturn(1);
        lenient().when(orderMapper.updateById(any(PurchaseOrder.class))).thenReturn(1);
    }

    /** 结算审批通过：仍核销，但不写订单 SETTLED（R5）。 */
    @Test
    void settlementApproved_doesNotSetOrderSettled_R5() {
        when(settlementMapper.selectById(SETTLE_ID)).thenReturn(settlement(SettlementStatus.PENDING));

        settlementService.handleApproval(9001L, SETTLE_ID, true, "ok");

        // 审批通过仍触发预算核销（占用→核销）
        verify(budgetOccupyService).writeOff(any());
        // R5：订单状态机不再流转 SETTLED——结清进度改派生展示字段
        verify(orderMapper, never()).updateById(any(PurchaseOrder.class));
    }

    /** 付款登记确认：付款单 PAID，但不写订单 PAID（R5）。 */
    @Test
    void confirmPayment_doesNotSetOrderPaid_R5() {
        Payment payment = payment(PaymentStatus.UNPAID);
        when(paymentMapper.selectById(PAY_ID)).thenReturn(payment);

        paymentService.confirmPayment(PAY_ID, "voucher-001.pdf", LocalDate.of(2026, 9, 24));

        assertEquals(PaymentStatus.PAID, payment.getStatus());
        // R5：订单状态机不再流转 PAID——付清进度改派生展示字段
        verify(orderMapper, never()).updateById(any(PurchaseOrder.class));
    }

    /** 订单派生进度：settleProgress = Σ已结/应结、paidProgress = Σ已付/应结（R5）。 */
    @Test
    void fillProgress_derivesSettleAndPaidProgress() {
        PurchaseOrder order = order(OrderStatus.RECEIVED);
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item())); // 应结 1200
        when(settlementMapper.sumSettledAmount(ORDER_ID)).thenReturn(new BigDecimal("600"));
        when(paymentMapper.sumPaidAmountByOrder(ORDER_ID)).thenReturn(new BigDecimal("300"));

        orderService.fillProgress(List.of(order));

        assertEquals(0, order.getSettleProgress().compareTo(new BigDecimal("0.5000")), "结清进度 600/1200");
        assertEquals(0, order.getPaidProgress().compareTo(new BigDecimal("0.2500")), "付清进度 300/1200");
    }

    // ---------------- fixtures ----------------

    private Settlement settlement(SettlementStatus status) {
        Settlement s = new Settlement();
        s.setId(SETTLE_ID);
        s.setOrderId(ORDER_ID);
        s.setSettleNo("JS-202609-000001");
        s.setAmount(new BigDecimal("1200"));
        s.setSettledQtyBase(new BigDecimal("12"));
        s.setStatus(status);
        return s;
    }

    private Payment payment(PaymentStatus status) {
        Payment p = new Payment();
        p.setId(PAY_ID);
        p.setSettlementId(SETTLE_ID);
        p.setPayNo("FK-202609-000001");
        p.setPayAmount(new BigDecimal("1200"));
        p.setStatus(status);
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
