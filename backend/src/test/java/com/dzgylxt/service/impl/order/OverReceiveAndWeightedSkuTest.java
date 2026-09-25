package com.dzgylxt.service.impl.order;

import com.dzgylxt.common.BizException;
import com.dzgylxt.entity.catalog.Sku;
import com.dzgylxt.entity.order.OrderItem;
import com.dzgylxt.enums.ValuationType;
import com.dzgylxt.mapper.catalog.SkuMapper;
import com.dzgylxt.security.LoginUser;
import com.dzgylxt.vo.order.ArrivalCreateReqVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * P4 R3b 超收 / 计重单测（设计 §5.2；AC 对齐规格 §5）：
 * 超收——阈值 0 直接拒绝；阈值 >0 且比例 ≤ 阈值须原因 + receipt:over-receive 权限放行；
 * 计重——合格量 ≤ 实到重量硬校验（PRD L811），计件 SKU 不校验。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OverReceiveAndWeightedSkuTest {

    @Mock
    private SkuMapper skuMapper;

    private ArrivalServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ArrivalServiceImpl();
        ReflectionTestUtils.setField(service, "skuMapper", skuMapper);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setUser(String... perms) {
        LoginUser u = new LoginUser();
        u.setId(9L);
        u.setUsername("keeper");
        u.setPerms(List.of(perms));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u, null, List.of()));
    }

    private ArrivalCreateReqVO.ItemActual actual(String remark, BigDecimal weight, BigDecimal qualified) {
        ArrivalCreateReqVO.ItemActual a = new ArrivalCreateReqVO.ItemActual();
        a.setRemark(remark);
        a.setActualWeight(weight);
        a.setQualifiedQty(qualified);
        return a;
    }

    // ==================== 超收 ====================

    @Test
    void overReceive_zeroThreshold_rejected() {
        setUser("receipt:over-receive");
        ReflectionTestUtils.setField(service, "overReceivePercent", new BigDecimal("0"));

        BizException e = assertThrows(BizException.class, () -> ReflectionTestUtils.invokeMethod(service,
                "checkOverReceive", new BigDecimal("100"), new BigDecimal("110"),
                actual("急用补货", null, null)));
        assertEquals(4000, e.getCode());
        assertTrue(e.getMessage().contains("禁止超收"));
    }

    @Test
    void overReceive_withinThreshold_withPermAndReason_allowed() {
        setUser("receipt:over-receive");
        ReflectionTestUtils.setField(service, "overReceivePercent", new BigDecimal("10"));
        // 超收 10% ≤ 阈值 10%：放行（无异常即通过）
        ReflectionTestUtils.invokeMethod(service, "checkOverReceive",
                new BigDecimal("100"), new BigDecimal("110"), actual("急用补货", null, null));
    }

    @Test
    void overReceive_overThreshold_rejected() {
        setUser("receipt:over-receive");
        ReflectionTestUtils.setField(service, "overReceivePercent", new BigDecimal("5"));

        BizException e = assertThrows(BizException.class, () -> ReflectionTestUtils.invokeMethod(service,
                "checkOverReceive", new BigDecimal("100"), new BigDecimal("110"),
                actual("急用补货", null, null)));
        assertTrue(e.getMessage().contains("超过授权阈值"));
    }

    @Test
    void overReceive_withoutReason_rejected() {
        setUser("receipt:over-receive");
        ReflectionTestUtils.setField(service, "overReceivePercent", new BigDecimal("10"));

        BizException e = assertThrows(BizException.class, () -> ReflectionTestUtils.invokeMethod(service,
                "checkOverReceive", new BigDecimal("100"), new BigDecimal("105"),
                actual(null, null, null)));
        assertTrue(e.getMessage().contains("超收必须填写原因"));
    }

    @Test
    void overReceive_withoutPerm_forbidden() {
        setUser("arrival:write");
        ReflectionTestUtils.setField(service, "overReceivePercent", new BigDecimal("10"));

        BizException e = assertThrows(BizException.class, () -> ReflectionTestUtils.invokeMethod(service,
                "checkOverReceive", new BigDecimal("100"), new BigDecimal("105"),
                actual("急用补货", null, null)));
        assertEquals(2004, e.getCode());
    }

    // ==================== 计重 ====================

    private OrderItem weightedItem() {
        OrderItem item = new OrderItem();
        item.setId(1L);
        item.setSkuId(88L);
        return item;
    }

    @Test
    void weightedSku_qualifiedOverWeight_rejected() {
        Sku sku = new Sku();
        sku.setId(88L);
        sku.setValuationType(ValuationType.BY_WEIGHT);
        when(skuMapper.selectById(88L)).thenReturn(sku);

        BizException e = assertThrows(BizException.class, () -> ReflectionTestUtils.invokeMethod(service,
                "checkWeightedSku", weightedItem(),
                actual(null, new BigDecimal("100.5"), new BigDecimal("101")),
                new BigDecimal("100.5")));
        assertTrue(e.getMessage().contains("合格量不得超过实到重量"));
    }

    @Test
    void weightedSku_qualifiedWithinWeight_stored() {
        Sku sku = new Sku();
        sku.setId(88L);
        sku.setValuationType(ValuationType.BY_WEIGHT);
        when(skuMapper.selectById(88L)).thenReturn(sku);

        @SuppressWarnings("unchecked")
        BigDecimal[] result = (BigDecimal[]) ReflectionTestUtils.invokeMethod(service,
                "checkWeightedSku", weightedItem(),
                actual(null, new BigDecimal("100.5"), new BigDecimal("99")),
                new BigDecimal("100.5"));
        assertArrayEquals(new BigDecimal[]{new BigDecimal("100.5"), new BigDecimal("99")}, result);
    }

    @Test
    void pieceSku_skipsWeightCheck() {
        Sku sku = new Sku();
        sku.setId(88L);
        sku.setValuationType(ValuationType.BY_PIECE);
        lenient().when(skuMapper.selectById(88L)).thenReturn(sku);

        Object result = ReflectionTestUtils.invokeMethod(service, "checkWeightedSku",
                weightedItem(), actual(null, null, null), new BigDecimal("10"));
        assertNull(result);
    }

    @Test
    void weightedSku_defaults_qualifiedEqualsActualWeight() {
        Sku sku = new Sku();
        sku.setId(88L);
        sku.setValuationType(ValuationType.BY_WEIGHT);
        when(skuMapper.selectById(88L)).thenReturn(sku);

        // 缺省：实到重量=实收数量、合格量=实收数量 → 相等放行
        @SuppressWarnings("unchecked")
        BigDecimal[] result = (BigDecimal[]) ReflectionTestUtils.invokeMethod(service,
                "checkWeightedSku", weightedItem(), null, new BigDecimal("50"));
        assertEquals(new BigDecimal("50"), result[0]);
        assertEquals(result[0], result[1]);
    }

    @Test
    void any_skuMissing_weightCheckSkipped() {
        when(skuMapper.selectById(any())).thenReturn(null);
        Object result = ReflectionTestUtils.invokeMethod(service, "checkWeightedSku",
                weightedItem(), null, new BigDecimal("10"));
        assertNull(result);
    }
}
