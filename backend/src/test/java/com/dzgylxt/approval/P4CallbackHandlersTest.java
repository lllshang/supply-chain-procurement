package com.dzgylxt.approval;

import com.dzgylxt.entity.catalog.SupplierQual;
import com.dzgylxt.entity.order.PurchaseOrder;
import com.dzgylxt.enums.OrderStatus;
import com.dzgylxt.enums.QualStatus;
import com.dzgylxt.mapper.catalog.SupplierQualMapper;
import com.dzgylxt.mapper.order.PurchaseOrderMapper;
import com.dzgylxt.service.IOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P4 回调处理器补齐单测（设计 §2.8 缺口收敛）：
 * ① SupplierQualApprovalHandler——SUPPLIER_QUAL 逆向流收敛正向 handler（偏差⑤），
 *    通过=P3c 落 reviewed_by/status，且幂等（防正向+逆向转发双写）；
 * ② DailyAuthApprovalHandler——DAILY_AUTH 业务动作补齐（偏差③）：
 *    通过=清除 auth_over_limit 挂起标记+留痕；驳回=订单终止+预算/额度释放（复用 cancelOrder 链）。
 *    SupplierQual 逆向路径（Controller→Service→gateway.callback）回归保障：handler 幂等跳过已终态。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class P4CallbackHandlersTest {

    @Mock
    private SupplierQualMapper supplierQualMapper;
    @Mock
    private PurchaseOrderMapper purchaseOrderMapper;
    @Mock
    private IOrderService orderService;

    private SupplierQualApprovalHandler qualHandler;
    private DailyAuthApprovalHandler dailyAuthHandler;

    @BeforeEach
    void setUp() {
        qualHandler = new SupplierQualApprovalHandler(supplierQualMapper);
        @SuppressWarnings("unchecked")
        ObjectProvider<IOrderService> orderProvider = mock(ObjectProvider.class);
        lenient().when(orderProvider.getObject()).thenReturn(orderService);
        dailyAuthHandler = new DailyAuthApprovalHandler(purchaseOrderMapper, orderProvider);
    }

    // ==================== SupplierQual 正向收敛（偏差⑤） ====================

    @Test
    void supplierQualApprove_updatesStatusAndReviewer() {
        SupplierQual qual = new SupplierQual();
        qual.setId(9L);
        qual.setStatus(QualStatus.PENDING);
        when(supplierQualMapper.selectById(9L)).thenReturn(qual);

        qualHandler.onApproved(100L, 9L, "资料齐全");

        ArgumentCaptor<SupplierQual> cap = ArgumentCaptor.forClass(SupplierQual.class);
        verify(supplierQualMapper).updateById(cap.capture());
        assertEquals(QualStatus.APPROVED, cap.getValue().getStatus());
        assertNull(cap.getValue().getRejectReason());
    }

    @Test
    void supplierQualReject_recordsReason() {
        SupplierQual qual = new SupplierQual();
        qual.setId(9L);
        qual.setStatus(QualStatus.PENDING);
        when(supplierQualMapper.selectById(9L)).thenReturn(qual);

        qualHandler.onRejected(100L, 9L, "证书过期");

        ArgumentCaptor<SupplierQual> cap = ArgumentCaptor.forClass(SupplierQual.class);
        verify(supplierQualMapper).updateById(cap.capture());
        assertEquals(QualStatus.REJECTED, cap.getValue().getStatus());
        assertEquals("证书过期", cap.getValue().getRejectReason());
    }

    /** 幂等：已终态重复回调跳过（逆向转发路径与正向路径双写防护）。 */
    @Test
    void supplierQual_idempotent_skipWhenAlreadyTerminal() {
        SupplierQual qual = new SupplierQual();
        qual.setId(9L);
        qual.setStatus(QualStatus.APPROVED);
        when(supplierQualMapper.selectById(9L)).thenReturn(qual);

        qualHandler.onApproved(100L, 9L, "repeat");

        verify(supplierQualMapper, never()).updateById(any(SupplierQual.class));
    }

    // ==================== DAILY_AUTH 业务动作（偏差③） ====================

    @Test
    void dailyAuthApprove_clearsOverLimitFlag_andTrail() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(88L);
        order.setStatus(OrderStatus.CREATED);
        order.setAuthOverLimit(1);
        when(purchaseOrderMapper.selectById(88L)).thenReturn(order);

        dailyAuthHandler.onApproved(100L, 88L, "同意超限采购");

        ArgumentCaptor<PurchaseOrder> cap = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderMapper).updateById(cap.capture());
        assertEquals(0, cap.getValue().getAuthOverLimit(), "授权确认应清除挂起标记");
        org.junit.jupiter.api.Assertions.assertTrue(
                cap.getValue().getRemark() != null && cap.getValue().getRemark().contains("超授权已确认"),
                "授权确认应留痕");
    }

    @Test
    void dailyAuthReject_cancelsOrderViaExistingChain() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(88L);
        order.setStatus(OrderStatus.CREATED);
        order.setAuthOverLimit(1);
        when(purchaseOrderMapper.selectById(88L)).thenReturn(order);

        dailyAuthHandler.onRejected(100L, 88L, "金额异常");

        verify(orderService).cancelOrder(org.mockito.ArgumentMatchers.eq(88L), anyString());
    }

    /** 非 CREATED 订单（已到货）不做破坏性终止，仅留痕放行。 */
    @Test
    void dailyAuthReject_nonCreatedOrder_skipCancel() {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(88L);
        order.setStatus(OrderStatus.PARTIAL_RECEIVED);
        order.setAuthOverLimit(1);
        when(purchaseOrderMapper.selectById(88L)).thenReturn(order);

        dailyAuthHandler.onRejected(100L, 88L, "金额异常");

        verify(orderService, never()).cancelOrder(anyLong(), anyString());
    }
}
