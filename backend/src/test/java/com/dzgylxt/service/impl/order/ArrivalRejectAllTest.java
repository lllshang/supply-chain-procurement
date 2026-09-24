package com.dzgylxt.service.impl.order;

import com.dzgylxt.common.BizException;
import com.dzgylxt.entity.order.Arrival;
import com.dzgylxt.entity.order.ArrivalItem;
import com.dzgylxt.enums.ArrivalStatus;
import com.dzgylxt.enums.HandleStatus;
import com.dzgylxt.enums.HandleType;
import com.dzgylxt.mapper.order.ArrivalItemMapper;
import com.dzgylxt.mapper.order.ArrivalMapper;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P3c-A4 整单拒收退货单测（PRD L827 状态机 / L809 原因+凭证必填 / L1115 状态全集）。
 *
 * <p>覆盖：正常整拒（合格量归零、差异行标退货、状态转 REJECTED_RETURNED）、
 * 缺原因/缺凭证拒绝、已全入库不可整拒、重复整拒幂等拒绝。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArrivalRejectAllTest {

    private static final long ARRIVAL_ID = 7101L;

    @Mock
    private ArrivalMapper arrivalMapper;

    @Mock
    private ArrivalItemMapper arrivalItemMapper;

    private ArrivalServiceImpl service;

    @BeforeEach
    void setUp() {
        service = Mockito.spy(new ArrivalServiceImpl());
        ReflectionTestUtils.setField(service, "arrivalItemMapper", arrivalItemMapper);
        ReflectionTestUtils.setField(service, "baseMapper", arrivalMapper);
    }

    private Arrival arrival(ArrivalStatus status) {
        Arrival a = new Arrival();
        a.setId(ARRIVAL_ID);
        a.setOrderId(7001L);
        a.setStatus(status);
        a.setActualQty(new BigDecimal("10"));
        return a;
    }

    private ArrivalItem item(BigDecimal stored, BigDecimal diff) {
        ArrivalItem i = new ArrivalItem();
        i.setId(7201L);
        i.setArrivalId(ARRIVAL_ID);
        i.setQtyStored(stored);
        i.setQtyDiff(diff);
        return i;
    }

    /** AC①：整拒（原因+凭证）→ 状态 REJECTED_RETURNED、合格量全 0、差异行标退货。 */
    @Test
    void rejectAll_withReasonAndVoucher_allQtyZeroAndStatusRejected() {
        Arrival a = arrival(ArrivalStatus.PARTIAL_STORED);
        when(arrivalMapper.selectById(ARRIVAL_ID)).thenReturn(a);
        when(arrivalItemMapper.selectList(any())).thenReturn(
                List.of(item(new BigDecimal("5"), new BigDecimal("2"))));

        service.rejectAll(ARRIVAL_ID, "整批质量不合格", "file/xxx.jpg");

        ArgumentCaptor<ArrivalItem> itemCaptor = ArgumentCaptor.forClass(ArrivalItem.class);
        verify(arrivalItemMapper).updateById(itemCaptor.capture());
        assertEquals(0, itemCaptor.getValue().getQtyStored().compareTo(BigDecimal.ZERO),
                "全部合格量置 0");
        assertEquals(HandleType.RETURN, itemCaptor.getValue().getHandleType(), "差异行标退货");
        assertEquals(HandleStatus.DONE, itemCaptor.getValue().getHandleStatus());

        assertEquals(ArrivalStatus.REJECTED_RETURNED, a.getStatus());
        assertEquals("整批质量不合格", a.getRemark());
        assertEquals("file/xxx.jpg", a.getVoucherFileKey());
        verify(arrivalMapper).updateById(a);
    }

    /** AC②：缺原因或凭证 → 拒绝。 */
    @Test
    void rejectAll_missingReasonOrVoucher_rejected() {
        when(arrivalMapper.selectById(ARRIVAL_ID)).thenReturn(arrival(ArrivalStatus.PARTIAL_STORED));

        BizException e1 = assertThrows(BizException.class,
                () -> service.rejectAll(ARRIVAL_ID, null, "file/xxx.jpg"));
        assertTrue(e1.getMessage().contains("必须填写原因"));

        BizException e2 = assertThrows(BizException.class,
                () -> service.rejectAll(ARRIVAL_ID, "不合格", null));
        assertTrue(e2.getMessage().contains("必须上传凭证"));
    }

    /** AC③：已全部入库单据 → 不可整拒。 */
    @Test
    void rejectAll_storedArrival_rejected() {
        when(arrivalMapper.selectById(ARRIVAL_ID)).thenReturn(arrival(ArrivalStatus.STORED));

        BizException e = assertThrows(BizException.class,
                () -> service.rejectAll(ARRIVAL_ID, "不合格", "file/xxx.jpg"));
        assertTrue(e.getMessage().contains("不可整单拒收"));
    }

    /** 幂等保护：已拒收单据重复操作 → 拒绝。 */
    @Test
    void rejectAll_alreadyRejected_idempotent() {
        when(arrivalMapper.selectById(ARRIVAL_ID))
                .thenReturn(arrival(ArrivalStatus.REJECTED_RETURNED));

        BizException e = assertThrows(BizException.class,
                () -> service.rejectAll(ARRIVAL_ID, "不合格", "file/xxx.jpg"));
        assertTrue(e.getMessage().contains("已拒收退货"));
    }
}
