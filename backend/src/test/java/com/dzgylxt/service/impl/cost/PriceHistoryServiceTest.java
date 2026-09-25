package com.dzgylxt.service.impl.cost;

import com.dzgylxt.entity.cost.PriceHistory;
import com.dzgylxt.enums.PriceAuditStatus;
import com.dzgylxt.enums.PriceSource;
import com.dzgylxt.mapper.cost.PriceHistoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P3-T07 价格库单测：异常价入待审 / recent 仅取通过价 / 空库兜底语义 / 审核状态机。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PriceHistoryServiceTest {

    private static final long SKU_ID = 5001L;

    @Mock
    private PriceHistoryMapper priceHistoryMapper;

    private PriceHistoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PriceHistoryServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", priceHistoryMapper);
        ReflectionTestUtils.setField(service, "abnormalPercent", 20);
    }

    /** 首价无基准 → 直接 APPROVED。 */
    @Test
    void record_firstPrice_approved() {
        when(priceHistoryMapper.insert(any(PriceHistory.class))).thenReturn(1);
        service.record(SKU_ID, 9001L, new BigDecimal("100"),
                PriceSource.ORDER, "ORDER", 1L, "首价");
        org.mockito.ArgumentCaptor<PriceHistory> cap =
                org.mockito.ArgumentCaptor.forClass(PriceHistory.class);
        verify(priceHistoryMapper).insert(cap.capture());
        assertEquals(PriceAuditStatus.APPROVED, cap.getValue().getAuditStatus());
    }

    /** 偏离近期均价超 20%（设计 §1.4 阈值）→ PENDING 待审（异常价入待审）。 */
    @Test
    void record_abnormalPrice_pending() {
        PriceHistory base = new PriceHistory();
        base.setSkuId(SKU_ID);
        base.setPrice(new BigDecimal("100"));
        when(priceHistoryMapper.insert(any(PriceHistory.class))).thenReturn(1);
        // recent 内部走 selectList（Wrappers 查询）——stub 返回历史均价 100
        when(priceHistoryMapper.selectList(any())).thenReturn(List.of(base));

        service.record(SKU_ID, 9001L, new BigDecimal("200"),
                PriceSource.ORDER, "ORDER", 2L, "异常高价");
        org.mockito.ArgumentCaptor<PriceHistory> cap =
                org.mockito.ArgumentCaptor.forClass(PriceHistory.class);
        verify(priceHistoryMapper).insert(cap.capture());
        assertEquals(PriceAuditStatus.PENDING, cap.getValue().getAuditStatus(),
                "偏离 100% > 20% 阈值应入待审");
    }

    /** 价格非法（≤0/null）静默忽略——埋点不阻断业务主流程。 */
    @Test
    void record_invalidPrice_silentSkip() {
        service.record(SKU_ID, 9001L, BigDecimal.ZERO, PriceSource.ORDER, "ORDER", 3L, null);
        service.record(SKU_ID, 9001L, null, PriceSource.ORDER, "ORDER", 3L, null);
        verify(priceHistoryMapper, org.mockito.Mockito.never()).insert(any(PriceHistory.class));
    }

    /** 审核：仅 PENDING 可流转（REJECTED/APPROVED 再审 → STATUS_INVALID）。 */
    @Test
    void audit_nonPending_rejected() {
        PriceHistory h = new PriceHistory();
        h.setId(1L);
        h.setAuditStatus(PriceAuditStatus.APPROVED);
        when(priceHistoryMapper.selectById(1L)).thenReturn(h);
        assertThrows(com.dzgylxt.common.BizException.class, () -> service.audit(1L, false, "x"));
    }
}
