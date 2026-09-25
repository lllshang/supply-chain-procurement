package com.dzgylxt.service.impl.order;

import com.dzgylxt.common.BizException;
import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.common.RedisLockUtil;
import com.dzgylxt.entity.contract.Contract;
import com.dzgylxt.entity.order.PurchaseOrder;
import com.dzgylxt.entity.purchase.PurchaseApply;
import com.dzgylxt.entity.purchase.PurchaseApplyItem;
import com.dzgylxt.enums.ContractStatus;
import com.dzgylxt.enums.ItemType;
import com.dzgylxt.enums.PurchaseApplyStatus;
import com.dzgylxt.mapper.catalog.UnitConversionMapper;
import com.dzgylxt.mapper.contract.ContractMapper;
import com.dzgylxt.mapper.contract.ContractPriceItemMapper;
import com.dzgylxt.mapper.order.OrderChangeMapper;
import com.dzgylxt.mapper.order.OrderItemMapper;
import com.dzgylxt.mapper.order.PurchaseOrderMapper;
import com.dzgylxt.mapper.purchase.AwardItemMapper;
import com.dzgylxt.mapper.purchase.AwardMapper;
import com.dzgylxt.mapper.purchase.InquiryMapper;
import com.dzgylxt.mapper.purchase.PurchaseApplyItemMapper;
import com.dzgylxt.mapper.purchase.PurchaseApplyMapper;
import com.dzgylxt.security.LoginUser;
import com.dzgylxt.vo.order.OrderCreateReqVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D16 无申请来源订单「需求来源」留痕专项测试（对应 docs/D16需求来源留痕_规格设计.md）。
 *
 * <p>复用 OrderTripleCheckTest 的下单三重校验 mock 装配基座，仅将请求改为
 * <b>无申请来源（applyId=null）= 日常采购</b>，验证需求来源校验与落库。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class D16DemandSourceTest {

    private static final long CONTRACT_ID = 500L;
    private static final long APP_ID = 700L;
    private static final long APP_ITEM_ID = 701L;

    @Mock
    private PurchaseOrderMapper purchaseOrderMapper;
    @Mock
    private ContractMapper contractMapper;
    @Mock
    private ContractPriceItemMapper contractPriceItemMapper;
    @Mock
    private PurchaseApplyItemMapper applyItemMapper;
    @Mock
    private PurchaseApplyMapper applyMapper;
    @Mock
    private InquiryMapper inquiryMapper;
    @Mock
    private AwardMapper awardMapper;
    @Mock
    private AwardItemMapper awardItemMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private OrderChangeMapper orderChangeMapper;
    @Mock
    private UnitConversionMapper unitConversionMapper;
    @Mock
    private RedisLockUtil redisLockUtil;
    @Mock
    private com.dzgylxt.service.IBudgetOccupyService budgetOccupyService;
    @Mock
    private com.dzgylxt.service.IPriceHistoryService priceHistoryService;
    @Mock
    private com.dzgylxt.approval.ApprovalGateway approvalGateway;

    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "contractMapper", contractMapper);
        when(contractPriceItemMapper.selectByContract(any(Long.class))).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "contractPriceItemMapper", contractPriceItemMapper);
        ReflectionTestUtils.setField(service, "applyItemMapper", applyItemMapper);
        ReflectionTestUtils.setField(service, "applyMapper", applyMapper);
        ReflectionTestUtils.setField(service, "inquiryMapper", inquiryMapper);
        ReflectionTestUtils.setField(service, "awardMapper", awardMapper);
        ReflectionTestUtils.setField(service, "awardItemMapper", awardItemMapper);
        ReflectionTestUtils.setField(service, "orderItemMapper", orderItemMapper);
        ReflectionTestUtils.setField(service, "orderChangeMapper", orderChangeMapper);
        ReflectionTestUtils.setField(service, "priceHistoryService", priceHistoryService);
        ReflectionTestUtils.setField(service, "arrivalMapper",
                org.mockito.Mockito.mock(com.dzgylxt.mapper.order.ArrivalMapper.class));
        ReflectionTestUtils.setField(service, "arrivalItemMapper",
                org.mockito.Mockito.mock(com.dzgylxt.mapper.order.ArrivalItemMapper.class));
        ReflectionTestUtils.setField(service, "unitConversionMapper", unitConversionMapper);
        ReflectionTestUtils.setField(service, "redisLockUtil", redisLockUtil);
        ReflectionTestUtils.setField(service, "businessNoGenerator", new BusinessNoGenerator(null) {
            private final AtomicInteger seq = new AtomicInteger();

            @Override
            public String nextNo(String prefix) {
                return prefix + "-TEST-" + String.format("%06d", seq.incrementAndGet());
            }
        });
        ReflectionTestUtils.setField(service, "budgetOccupyService", budgetOccupyService);
        ReflectionTestUtils.setField(service, "approvalGateway", approvalGateway);
        lenient().when(budgetOccupyService.transfer(any())).thenReturn(
                com.dzgylxt.vo.budget.OccupyResultVO.ok(BigDecimal.ZERO, List.of()));
        lenient().when(budgetOccupyService.release(any())).thenReturn(
                com.dzgylxt.vo.budget.OccupyResultVO.ok(BigDecimal.ZERO, List.of()));
        lenient().when(budgetOccupyService.occupiedTotal(any(), any())).thenReturn(BigDecimal.ZERO);

        ReflectionTestUtils.setField(service, "baseMapper", purchaseOrderMapper);
        lenient().when(purchaseOrderMapper.insert(any(PurchaseOrder.class))).thenReturn(1);

        lenient().when(redisLockUtil.tryLock(any(), anyLong())).thenReturn("token");
        lenient().doNothing().when(redisLockUtil).unlock(any(), any());

        stubContractMapper();
        stubUnitConversion();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void stubContractMapper() {
        when(contractMapper.selectForUpdate(CONTRACT_ID)).thenAnswer(inv -> {
            Contract c = baseContract();
            c.setAvailableAmount(new BigDecimal("2000000"));
            c.setVersion(0);
            return c;
        });
        when(contractMapper.deductAvailable(eq(CONTRACT_ID), any(BigDecimal.class), any(Integer.class)))
                .thenAnswer(inv -> {
                    BigDecimal delta = inv.getArgument(1);
                    return delta.compareTo(new BigDecimal("2000000")) <= 0 ? 1 : 0;
                });
        when(contractMapper.selectById(CONTRACT_ID)).thenAnswer(inv -> {
            Contract c = baseContract();
            c.setAvailableAmount(new BigDecimal("2000000"));
            return c;
        });
        lenient().when(contractMapper.updateById(any(Contract.class))).thenReturn(1);
    }

    private void stubUnitConversion() {
        com.dzgylxt.entity.catalog.UnitConversion conv = new com.dzgylxt.entity.catalog.UnitConversion();
        conv.setRate(BigDecimal.ONE);
        lenient().when(unitConversionMapper.selectCurrentEffective(anyLong(), any(), any())).thenReturn(conv);
    }

    private Contract baseContract() {
        Contract c = new Contract();
        c.setId(CONTRACT_ID);
        c.setStatus(ContractStatus.EFFECTIVE);
        c.setAmount(new BigDecimal("2000000"));
        c.setValidFrom(LocalDate.now().minusDays(1));
        c.setValidTo(LocalDate.now().plusDays(30));
        return c;
    }

    /** 日常采购请求（无申请来源），reason 为 null（用于触发必填校验）。 */
    private OrderCreateReqVO dailyReq(BigDecimal qty, BigDecimal price) {
        return dailyReqWithReason(qty, price, null);
    }

    private OrderCreateReqVO dailyReqWithReason(BigDecimal qty, BigDecimal price, String reason) {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setContractId(CONTRACT_ID);
        req.setApplyId(null); // ← 日常采购：无申请来源
        if (reason != null) {
            req.setSourceReason(reason);
        }
        OrderCreateReqVO.OrderItemReqVO item = new OrderCreateReqVO.OrderItemReqVO();
        item.setSkuId(9L);
        item.setQty(qty);
        item.setPurchaseUnit("PCS");
        item.setPrice(price);
        item.setItemType(ItemType.MATERIAL);
        req.setItems(List.of(item));
        return req;
    }

    private void setUser(boolean withPerms) {
        LoginUser u = new LoginUser();
        u.setId(99L);
        u.setUsername("authuser");
        u.setMainDeptId(1L);
        u.setPerms(withPerms ? List.of("order:create") : List.of());
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(u, null, List.of()));
    }

    /** AC1：无申请来源订单未填写需求来源说明 → 抛 PARAM_ERROR。 */
    @Test
    void dailyOrder_withoutSourceReason_throws() {
        setUser(true);
        BizException ex = assertThrows(BizException.class,
                () -> service.createOrder(dailyReq(BigDecimal.ONE, new BigDecimal("100"))));
        assertTrue(ex.getMessage().contains("需求来源"), ex.getMessage());
    }

    /** AC2：无申请来源订单填写需求来源说明 → 落库 source_type=OFFLINE + source_reason=输入值。 */
    @Test
    void dailyOrder_withSourceReason_recordsOfflineSource() {
        setUser(true);
        service.createOrder(dailyReqWithReason(BigDecimal.ONE, new BigDecimal("100"), "月度办公耗材补货"));

        ArgumentCaptor<PurchaseOrder> cap = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderMapper).insert(cap.capture());
        PurchaseOrder saved = cap.getValue();
        assertEquals("OFFLINE", saved.getSourceType());
        assertEquals("月度办公耗材补货", saved.getSourceReason());
    }

    /** AC3：标准/项目链路（applyId!=null）→ source_type=APPLY，source_reason 可为空、不受强制校验。 */
    @Test
    void applyBasedOrder_recordsSourceTypeApply_withoutReason() {
        stubApplyMappers();
        service.createOrder(applyReq(BigDecimal.ONE, new BigDecimal("100")));

        ArgumentCaptor<PurchaseOrder> cap = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderMapper).insert(cap.capture());
        assertEquals("APPLY", cap.getValue().getSourceType());
        assertNull(cap.getValue().getSourceReason());
    }

    /** AC4：无申请来源订单同时具备授权留痕（D14）+ 需求来源留痕（D16），审计三件套闭合。 */
    @Test
    void dailyOrder_authAndSourceTogether_recorded() {
        setUser(true);
        service.createOrder(dailyReqWithReason(BigDecimal.ONE, new BigDecimal("100"), "设备维保备件"));

        ArgumentCaptor<PurchaseOrder> cap = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderMapper).insert(cap.capture());
        PurchaseOrder saved = cap.getValue();
        // D14 授权留痕
        assertEquals(99L, saved.getAuthorizedBy());
        assertEquals("authuser", saved.getAuthorizedName());
        assertNotNull(saved.getAuthorizedTime());
        // D16 需求来源留痕
        assertEquals("OFFLINE", saved.getSourceType());
        assertEquals("设备维保备件", saved.getSourceReason());
    }

    private OrderCreateReqVO applyReq(BigDecimal qty, BigDecimal price) {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setContractId(CONTRACT_ID);
        req.setApplyId(APP_ID);
        OrderCreateReqVO.OrderItemReqVO item = new OrderCreateReqVO.OrderItemReqVO();
        item.setSkuId(9L);
        item.setApplyItemId(APP_ITEM_ID);
        item.setQty(qty);
        item.setPrice(price);
        item.setItemType(ItemType.MATERIAL);
        req.setItems(List.of(item));
        return req;
    }

    private void stubApplyMappers() {
        PurchaseApply apply = new PurchaseApply();
        apply.setId(APP_ID);
        apply.setStatus(PurchaseApplyStatus.APPROVED);
        lenient().when(applyMapper.selectById(APP_ID)).thenReturn(apply);

        PurchaseApplyItem ai = new PurchaseApplyItem();
        ai.setId(APP_ITEM_ID);
        ai.setApplyId(APP_ID);
        ai.setSkuId(9L);
        ai.setApplyQty(new BigDecimal("100"));
        ai.setOrderedQty(BigDecimal.ZERO);
        ai.setRemainQty(new BigDecimal("100"));
        ai.setVersion(0);
        ai.setPurchaseUnit("PCS");
        lenient().when(applyItemMapper.selectForUpdateByIds(List.of(APP_ITEM_ID))).thenReturn(List.of(ai));
        lenient().when(applyItemMapper.selectById(APP_ITEM_ID)).thenReturn(ai);
        lenient().when(applyItemMapper.deductRemain(eq(APP_ITEM_ID), any(BigDecimal.class), any(Integer.class))).thenReturn(1);
        lenient().when(applyItemMapper.selectList(any())).thenReturn(List.of(ai));
        lenient().when(applyMapper.updateById(any(PurchaseApply.class))).thenReturn(1);
    }
}
