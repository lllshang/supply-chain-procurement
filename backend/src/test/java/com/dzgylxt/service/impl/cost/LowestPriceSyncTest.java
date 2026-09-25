package com.dzgylxt.service.impl.cost;

import com.dzgylxt.entity.catalog.Sku;
import com.dzgylxt.entity.cost.PriceHistory;
import com.dzgylxt.entity.order.OrderItem;
import com.dzgylxt.entity.order.PurchaseOrder;
import com.dzgylxt.enums.OrderStatus;
import com.dzgylxt.mapper.catalog.SkuMapper;
import com.dzgylxt.mapper.cost.PriceHistoryMapper;
import com.dzgylxt.mapper.order.OrderItemMapper;
import com.dzgylxt.mapper.order.PurchaseOrderMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P4 R3c 最低价同步标准价单测（设计 §5.3；AC 对齐规格 §6）：
 * 开关关闭零行为；开启后取同 SKU+口径 已完成订单最低正数成交价写 sku.standard_price，
 * 同步动作经 price_history 留痕；同价幂等不重复写。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LowestPriceSyncTest {

    private static final long ORDER_ID = 700L;
    private static final long SKU_ID = 88L;

    @Mock
    private PriceHistoryMapper priceHistoryMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private PurchaseOrderMapper purchaseOrderMapper;
    @Mock
    private SkuMapper skuMapper;

    private PriceHistoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PriceHistoryServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", priceHistoryMapper);
        ReflectionTestUtils.setField(service, "orderItemMapper", orderItemMapper);
        ReflectionTestUtils.setField(service, "purchaseOrderMapper", purchaseOrderMapper);
        ReflectionTestUtils.setField(service, "skuMapper", skuMapper);
        ReflectionTestUtils.setField(service, "lowestPriceSyncEnabled", true);

        lenient().when(priceHistoryMapper.selectList(any())).thenReturn(List.of());
        lenient().when(priceHistoryMapper.insert(any(PriceHistory.class))).thenReturn(1);
    }

    private OrderItem item(Long orderId, String price, String conv) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setSkuId(SKU_ID);
        item.setPrice(new BigDecimal(price));
        item.setConvSnapshot(conv);
        return item;
    }

    private PurchaseOrder order(Long id, OrderStatus status) {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(id);
        po.setStatus(status);
        po.setSupplierId(5L);
        return po;
    }

    @Test
    void disabled_zeroBehavior() {
        ReflectionTestUtils.setField(service, "lowestPriceSyncEnabled", false);
        int updated = service.syncLowestPriceOnOrderReceived(ORDER_ID);
        assertEquals(0, updated);
        verify(skuMapper, never()).updateById(any(Sku.class));
    }

    @Test
    void enabled_syncsLowestPrice_sameCalibre_only() {
        // 当前订单行：SKU 88 @110，口径 PCS@1
        when(orderItemMapper.selectList(any())).thenAnswer(inv -> {
            // 两次调用：第一次=当前订单明细；第二次=同 SKU 候选集
            Object wrapper = inv.getArgument(0);
            return java.util.List.of(item(ORDER_ID, "110", "{\"purchaseUnit\":\"PCS\",\"rate\":\"1\"}"));
        });
        when(orderItemMapper.selectList(any()))
                .thenReturn(java.util.List.of(item(ORDER_ID, "110", "{\"purchaseUnit\":\"PCS\",\"rate\":\"1\"}")))
                .thenReturn(java.util.List.of(
                        item(701L, "105", "{\"purchaseUnit\":\"PCS\",\"rate\":\"1\"}"),
                        item(702L, "90", "{\"purchaseUnit\":\"BOX\",\"rate\":\"10\"}"), // 跨口径不计
                        item(703L, "0", "{\"purchaseUnit\":\"PCS\",\"rate\":\"1\"}"))); // 非正数不计
        when(purchaseOrderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, OrderStatus.RECEIVED));
        when(purchaseOrderMapper.selectById(701L)).thenReturn(order(701L, OrderStatus.RECEIVED));
        when(purchaseOrderMapper.selectById(702L)).thenReturn(order(702L, OrderStatus.RECEIVED));
        when(purchaseOrderMapper.selectById(703L)).thenReturn(order(703L, OrderStatus.RECEIVED));
        Sku sku = new Sku();
        sku.setId(SKU_ID);
        sku.setStandardPrice(new BigDecimal("120"));
        when(skuMapper.selectById(SKU_ID)).thenReturn(sku);

        int updated = service.syncLowestPriceOnOrderReceived(ORDER_ID);

        assertEquals(1, updated);
        ArgumentCaptor<Sku> cap = ArgumentCaptor.forClass(Sku.class);
        verify(skuMapper).updateById(cap.capture());
        // 同口径（PCS@1）已完成订单最低正数成交价 = 105（BOX@10 口径与零价排除）
        assertEquals(new BigDecimal("105.00"), cap.getValue().getStandardPrice());
        // 留痕：price_history 通道
        ArgumentCaptor<PriceHistory> hist = ArgumentCaptor.forClass(PriceHistory.class);
        verify(priceHistoryMapper).insert(hist.capture());
        assertEquals("PRICE_SYNC", hist.getValue().getBizType());
    }

    @Test
    void enabled_sameStandardPrice_idempotentNoWrite() {
        when(orderItemMapper.selectList(any()))
                .thenReturn(java.util.List.of(item(ORDER_ID, "110", "{\"purchaseUnit\":\"PCS\",\"rate\":\"1\"}")))
                .thenReturn(java.util.List.of(item(701L, "105", "{\"purchaseUnit\":\"PCS\",\"rate\":\"1\"}")));
        when(purchaseOrderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, OrderStatus.RECEIVED));
        when(purchaseOrderMapper.selectById(701L)).thenReturn(order(701L, OrderStatus.RECEIVED));
        Sku sku = new Sku();
        sku.setId(SKU_ID);
        sku.setStandardPrice(new BigDecimal("105.00"));
        when(skuMapper.selectById(SKU_ID)).thenReturn(sku);

        int updated = service.syncLowestPriceOnOrderReceived(ORDER_ID);

        assertEquals(0, updated);
        verify(skuMapper, never()).updateById(any(Sku.class));
    }

    @Test
    void enabled_cancelledOrders_excluded() {
        when(orderItemMapper.selectList(any()))
                .thenReturn(java.util.List.of(item(ORDER_ID, "110", "{\"purchaseUnit\":\"PCS\",\"rate\":\"1\"}")))
                // 候选集 = 同 SKU 全部订单行（含当前订单行，真实 DB 语义）
                .thenReturn(java.util.List.of(
                        item(ORDER_ID, "110", "{\"purchaseUnit\":\"PCS\",\"rate\":\"1\"}"),
                        item(704L, "80", "{\"purchaseUnit\":\"PCS\",\"rate\":\"1\"}")));
        when(purchaseOrderMapper.selectById(ORDER_ID)).thenReturn(order(ORDER_ID, OrderStatus.RECEIVED));
        when(purchaseOrderMapper.selectById(704L)).thenReturn(order(704L, OrderStatus.CANCELLED));
        Sku sku = new Sku();
        sku.setId(SKU_ID);
        when(skuMapper.selectById(SKU_ID)).thenReturn(sku);

        int updated = service.syncLowestPriceOnOrderReceived(ORDER_ID);

        assertEquals(1, updated);
        ArgumentCaptor<Sku> cap = ArgumentCaptor.forClass(Sku.class);
        verify(skuMapper).updateById(cap.capture());
        // 已取消订单的 80 不计入：仅剩当前订单价 110
        assertEquals(new BigDecimal("110.00"), cap.getValue().getStandardPrice());
    }
}
