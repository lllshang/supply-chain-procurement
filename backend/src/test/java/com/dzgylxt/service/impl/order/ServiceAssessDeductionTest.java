package com.dzgylxt.service.impl.order;

import com.dzgylxt.common.BizException;
import com.dzgylxt.entity.order.OrderItem;
import com.dzgylxt.entity.order.PurchaseOrder;
import com.dzgylxt.entity.order.ServiceAssess;
import com.dzgylxt.entity.order.ServiceDeductionItem;
import com.dzgylxt.enums.AssessStatus;
import com.dzgylxt.enums.ItemType;
import com.dzgylxt.mapper.order.OrderItemMapper;
import com.dzgylxt.mapper.order.PurchaseOrderMapper;
import com.dzgylxt.mapper.order.ServiceAssessMapper;
import com.dzgylxt.mapper.order.ServiceDeductionItemMapper;
import com.dzgylxt.vo.order.ServiceAssessSaveReqVO;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P3c-A5 服务扣款明细单测（PRD L812 一条或多条；BR-15 L1090；应付非负）。
 *
 * <p>覆盖：多条明细 → deduct_amount = Σ 且明细落子表；明细校验（缺项目名/金额非正）；
 * Σ 超应付基数拒绝；无明细（旧单值场景）兼容不写子表。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceAssessDeductionTest {

    private static final long ORDER_ID = 8301L;

    @Mock
    private PurchaseOrderMapper orderMapper;

    @Mock
    private ServiceAssessMapper assessMapper;

    @Mock
    private ServiceDeductionItemMapper deductionItemMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    private ServiceAssessServiceImpl service;

    @BeforeEach
    void setUp() {
        service = Mockito.spy(new ServiceAssessServiceImpl());
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "baseMapper", assessMapper);
        ReflectionTestUtils.setField(service, "deductionItemMapper", deductionItemMapper);
        ReflectionTestUtils.setField(service, "orderItemMapper", orderItemMapper);

        PurchaseOrder order = new PurchaseOrder();
        order.setId(ORDER_ID);
        order.setOrderType(ItemType.SERVICE);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order);

        // 订单金额 = 100 × 10 = 1000（应付基数）
        OrderItem item = new OrderItem();
        item.setOrderId(ORDER_ID);
        item.setPrice(new BigDecimal("100"));
        item.setQtyBase(new BigDecimal("10"));
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item));
        // 无历史考核
        when(assessMapper.selectList(any())).thenReturn(List.of());
    }

    private ServiceAssessSaveReqVO req(List<ServiceAssessSaveReqVO.DeductionItemVO> items,
                                       BigDecimal single) {
        ServiceAssessSaveReqVO req = new ServiceAssessSaveReqVO();
        req.setOrderId(ORDER_ID);
        req.setAssessDate(LocalDate.of(2026, 9, 24));
        req.setDeductionItems(items);
        req.setDeductAmount(single);
        return req;
    }

    private ServiceAssessSaveReqVO.DeductionItemVO vo(String name, BigDecimal amount) {
        ServiceAssessSaveReqVO.DeductionItemVO vo = new ServiceAssessSaveReqVO.DeductionItemVO();
        vo.setItemName(name);
        vo.setAmount(amount);
        vo.setReason("考核未达标");
        return vo;
    }

    /** AC①：2 条明细 → deduct_amount = Σ，且明细落子表。 */
    @Test
    void assess_twoDeductionItems_sumAndPersists() {
        service.assess(req(List.of(vo("响应时效", new BigDecimal("300")),
                vo("服务质量", new BigDecimal("200"))), null));

        ArgumentCaptor<ServiceAssess> captor = ArgumentCaptor.forClass(ServiceAssess.class);
        verify(service).save(captor.capture());
        assertEquals(0, captor.getValue().getDeductAmount().compareTo(new BigDecimal("500")),
                "汇总冗余 = Σ 明细");

        ArgumentCaptor<ServiceDeductionItem> itemCaptor =
                ArgumentCaptor.forClass(ServiceDeductionItem.class);
        verify(deductionItemMapper, Mockito.times(2)).insert(itemCaptor.capture());
        assertEquals("响应时效", itemCaptor.getAllValues().get(0).getItemName());
        assertEquals(0, itemCaptor.getAllValues().get(1).getAmount()
                .compareTo(new BigDecimal("200")));
    }

    /** 明细校验：缺项目名 / 金额非正 → 拒绝。 */
    @Test
    void assess_invalidDeductionItem_rejected() {
        BizException e1 = assertThrows(BizException.class,
                () -> service.assess(req(List.of(vo(null, new BigDecimal("100"))), null)));
        assertTrue(e1.getMessage().contains("必须填写扣款项目"));

        BizException e2 = assertThrows(BizException.class,
                () -> service.assess(req(List.of(vo("时效", new BigDecimal("0"))), null)));
        assertTrue(e2.getMessage().contains("必须大于 0"));
    }

    /** AC②：Σ 明细超应付基数（1000）→ 拒绝（不得导致应付为负）。 */
    @Test
    void assess_deductionExceedsPayable_rejected() {
        BizException e = assertThrows(BizException.class, () -> service.assess(req(
                List.of(vo("重大事故", new BigDecimal("800")),
                        vo("违约", new BigDecimal("500"))), null)));
        assertTrue(e.getMessage().contains("超出订单应付基数"), "实际：" + e.getMessage());
    }

    /** AC④：无明细（旧单值场景）→ 兼容，不写子表。 */
    @Test
    void assess_noDeductionItems_usesSingleAndSkipsSubTable() {
        service.assess(req(null, new BigDecimal("150")));

        ArgumentCaptor<ServiceAssess> captor = ArgumentCaptor.forClass(ServiceAssess.class);
        verify(service).save(captor.capture());
        assertEquals(0, captor.getValue().getDeductAmount().compareTo(new BigDecimal("150")));
        verify(deductionItemMapper, Mockito.never())
                .insert(any(ServiceDeductionItem.class));
    }
}
