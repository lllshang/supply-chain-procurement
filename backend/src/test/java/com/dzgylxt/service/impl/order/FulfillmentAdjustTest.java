package com.dzgylxt.service.impl.order;

import com.dzgylxt.common.BizException;
import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.entity.order.FulfillmentAdjust;
import com.dzgylxt.entity.order.PurchaseOrder;
import com.dzgylxt.enums.AdjustStatus;
import com.dzgylxt.enums.AdjustType;
import com.dzgylxt.enums.ItemType;
import com.dzgylxt.enums.OrderStatus;
import com.dzgylxt.mapper.order.FulfillmentAdjustMapper;
import com.dzgylxt.mapper.order.PurchaseOrderMapper;
import com.dzgylxt.vo.order.AdjustSaveReqVO;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R3 履约调整单测：取消 5% 免审——调整一律进审批（FULFILLMENT_ADJUST）；
 * 放开"部分到货可调整"（仅全部完成/已结算/已取消不可调整）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FulfillmentAdjustTest {

    private static final long ORDER_ID = 8101L;
    private static final long ADJUST_ID = 8201L;

    @Mock
    private PurchaseOrderMapper orderMapper;

    @Mock
    private FulfillmentAdjustMapper adjustMapper;

    @Mock
    private BusinessNoGenerator businessNoGenerator;

    @Mock
    private com.dzgylxt.approval.ApprovalGateway approvalGateway;

    private FulfillmentAdjustServiceImpl service;

    @BeforeEach
    void setUp() {
        service = Mockito.spy(new FulfillmentAdjustServiceImpl());
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "businessNoGenerator", businessNoGenerator);
        ReflectionTestUtils.setField(service, "approvalGateway", approvalGateway);
        ReflectionTestUtils.setField(service, "baseMapper", adjustMapper);
        when(businessNoGenerator.nextNo(anyString())).thenReturn("LY-202609-000001");
        when(adjustMapper.insert(any(FulfillmentAdjust.class))).thenReturn(1);
        when(adjustMapper.updateById(any(FulfillmentAdjust.class))).thenReturn(1);
        Mockito.doReturn(true).when(service).updateById(any(FulfillmentAdjust.class));
        Mockito.doAnswer(inv -> {
            FulfillmentAdjust a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", ADJUST_ID);
            return true;
        }).when(service).save(any(FulfillmentAdjust.class));
    }

    private PurchaseOrder order(OrderStatus status) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(ORDER_ID);
        order.setOrderNo("DD-TEST-000810");
        order.setStatus(status);
        order.setOrderType(ItemType.MATERIAL);
        return order;
    }

    private AdjustSaveReqVO req() {
        AdjustSaveReqVO vo = new AdjustSaveReqVO();
        vo.setOrderId(ORDER_ID);
        vo.setAdjustType(AdjustType.CHANGE);
        vo.setReason("数量调整");
        vo.setAmount(new BigDecimal("100"));
        return vo;
    }

    /** R3：部分到货（PARTIAL_RECEIVED）可调整（原"仅未到货"限制放开）。 */
    @Test
    void createAdjust_partialReceived_allowed() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.PARTIAL_RECEIVED));
        service.createAdjust(req());
        verify(service).save(any(FulfillmentAdjust.class));
    }

    /** R3：全部完成（RECEIVED）/已结算（SETTLED）/已付款（PAID）不可调整。 */
    @Test
    void createAdjust_completedOrSettled_rejected() {
        for (OrderStatus status : new OrderStatus[]{OrderStatus.RECEIVED, OrderStatus.SETTLED, OrderStatus.PAID}) {
            when(orderMapper.selectById(ORDER_ID)).thenReturn(order(status));
            BizException e = assertThrows(BizException.class, () -> service.createAdjust(req()),
                    "状态 " + status + " 应拒绝调整");
            assertTrue(e.getMessage().contains("不可调整"), e.getMessage());
        }
    }

    /** R3：提交一律进审批——5% 免审分支已取消（即使金额为 0 也走 FULFILLMENT_ADJUST）。 */
    @Test
    void submit_alwaysGoesApproval() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.CREATED));
        FulfillmentAdjust adjust = new FulfillmentAdjust();
        adjust.setId(ADJUST_ID);
        adjust.setOrderId(ORDER_ID);
        adjust.setAdjustType(AdjustType.CHANGE);
        adjust.setStatus(AdjustStatus.DRAFT);
        when(adjustMapper.selectById(ADJUST_ID)).thenReturn(adjust);

        service.submit(ADJUST_ID);

        assertEquals(AdjustStatus.IN_APPROVAL, adjust.getStatus(), "R3：一律进审批，无免审直生效");
        ArgumentCaptor<com.dzgylxt.approval.ApprovalTaskSpec> spec =
                ArgumentCaptor.forClass(com.dzgylxt.approval.ApprovalTaskSpec.class);
        verify(approvalGateway).create(spec.capture());
        assertEquals(FulfillmentAdjustServiceImpl.BIZ_TYPE, spec.getValue().getBizType());
    }
}
