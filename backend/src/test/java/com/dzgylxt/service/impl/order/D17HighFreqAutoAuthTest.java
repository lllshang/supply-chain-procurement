package com.dzgylxt.service.impl.order;

import com.dzgylxt.common.BizException;
import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.common.RedisLockUtil;
import com.dzgylxt.entity.contract.Contract;
import com.dzgylxt.entity.order.PurchaseOrder;
import com.dzgylxt.enums.ContractStatus;
import com.dzgylxt.enums.ItemType;
import com.dzgylxt.mapper.catalog.UnitConversionMapper;
import com.dzgylxt.mapper.contract.ContractMapper;
import com.dzgylxt.mapper.contract.ContractPriceItemMapper;
import com.dzgylxt.mapper.contract.ContractSkuWhitelistMapper;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D17 高频补货自动通过授权专项测试（对应 docs/D17高频补货自动授权_规格设计.md）。
 *
 * <p>复用 D14 下单 mock 装配基座：无申请来源（applyId=null）= 日常采购。验证开关开启时
 * 跳过 DAILY_AUTH 人工升级任务、置 {@code auto_authorized=1}、仍保留授权留痕，且不跳过合同/预算闸门。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class D17HighFreqAutoAuthTest {

    private static final long CONTRACT_ID = 500L;

    @Mock
    private PurchaseOrderMapper purchaseOrderMapper;
    @Mock
    private ContractMapper contractMapper;
    @Mock
    private ContractPriceItemMapper contractPriceItemMapper;
    @Mock
    private ContractSkuWhitelistMapper contractSkuWhitelistMapper;
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

    private final BigDecimal AUTH_LIMIT = new BigDecimal("500000");

    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "contractMapper", contractMapper);
        when(contractPriceItemMapper.selectByContract(any(Long.class))).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "contractPriceItemMapper", contractPriceItemMapper);
        when(contractSkuWhitelistMapper.selectList(any())).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "contractSkuWhitelistMapper", contractSkuWhitelistMapper);
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

        // D14：默认开启 + 50 万阈值
        ReflectionTestUtils.setField(service, "dailyAuthEnabled", true);
        ReflectionTestUtils.setField(service, "dailyAuthLimit", AUTH_LIMIT);
        // D17：默认关（与 @Value 默认一致），需显式开启的测试自行 setField 为 true
        ReflectionTestUtils.setField(service, "dailyAutoAuthEnabled", false);
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
        when(contractMapper.deductAvailable(eq(CONTRACT_ID), any(BigDecimal.class), anyInt()))
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

    private OrderCreateReqVO dailyReq(BigDecimal qty, BigDecimal price) {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setContractId(CONTRACT_ID);
        req.setApplyId(null);
        OrderCreateReqVO.OrderItemReqVO item = new OrderCreateReqVO.OrderItemReqVO();
        item.setSkuId(9L);
        item.setQty(qty);
        item.setPurchaseUnit("PCS");
        item.setPrice(price);
        item.setItemType(ItemType.MATERIAL);
        req.setItems(List.of(item));
        req.setSourceReason("日常补货");
        return req;
    }

    private void setUser(boolean withPerms) {
        LoginUser u = new LoginUser();
        u.setId(99L);
        u.setUsername("authuser");
        u.setMainDeptId(1L);
        u.setPerms(withPerms ? List.of("order:create") : List.of());
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(u, null, java.util.List.of()));
    }

    // ==================== D17 AC ====================

    @Test
    void switchOff_overLimit_stillCreatesDailyAuthTask_andAutoAuthorizedZero() {
        // 默认关：行为与 D14 一致（回归保护）
        setUser(true);
        service.createOrder(dailyReq(BigDecimal.ONE, new BigDecimal("600000")));

        ArgumentCaptor<PurchaseOrder> cap = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderMapper).insert(cap.capture());
        assertEquals(1, cap.getValue().getAuthOverLimit());
        assertEquals(0, cap.getValue().getAutoAuthorized());

        verify(approvalGateway, org.mockito.Mockito.times(1)).create(argThat(
                spec -> "DAILY_AUTH".equals(spec.getBizType())));
    }

    @Test
    void switchOn_overLimit_noDailyAuthTask_andAutoAuthorizedOne_andTrailKept() {
        ReflectionTestUtils.setField(service, "dailyAutoAuthEnabled", true);
        setUser(true);
        // 金额 60 万 > 50 万阈值：开关开启 → 系统自动通过，不生成 DAILY_AUTH 任务
        service.createOrder(dailyReq(BigDecimal.ONE, new BigDecimal("600000")));

        ArgumentCaptor<PurchaseOrder> cap = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderMapper).insert(cap.capture());
        PurchaseOrder saved = cap.getValue();
        assertEquals(0, saved.getAuthOverLimit());
        assertEquals(1, saved.getAutoAuthorized());
        // 授权留痕仍保留
        assertEquals(99L, saved.getAuthorizedBy());
        assertEquals("authuser", saved.getAuthorizedName());
        assertNotNull(saved.getAuthorizedTime());
        // 关键：未生成 DAILY_AUTH 升级任务
        verify(approvalGateway, org.mockito.Mockito.never()).create(any());
    }

    @Test
    void switchOn_userNoPerms_stillRejected_byAuthGate() {
        ReflectionTestUtils.setField(service, "dailyAutoAuthEnabled", true);
        setUser(false);
        BizException ex = assertThrows(BizException.class,
                () -> service.createOrder(dailyReq(BigDecimal.ONE, new BigDecimal("600000"))));
        assertTrue(ex.getMessage().contains("无日常采购授权"), ex.getMessage());
    }

    @Test
    void switchOn_overContractAmount_stillRejected_byContractGate() {
        ReflectionTestUtils.setField(service, "dailyAutoAuthEnabled", true);
        setUser(true);
        // 金额 300 万 > 合同可用 200 万：合同闸不跳过，仍拒
        BizException ex = assertThrows(BizException.class,
                () -> service.createOrder(dailyReq(BigDecimal.ONE, new BigDecimal("3000000"))));
        assertTrue(ex.getMessage().contains("超出合同可用额度"), ex.getMessage());
    }

    @Test
    void switchOn_underLimit_autoAuthorizedOne_noDailyAuthTask() {
        ReflectionTestUtils.setField(service, "dailyAutoAuthEnabled", true);
        setUser(true);
        // 金额 10 < 阈值：本就无人升级；开关开启标记 auto_authorized=1（系统自动授权口径）
        service.createOrder(dailyReq(BigDecimal.ONE, new BigDecimal("10")));

        ArgumentCaptor<PurchaseOrder> cap = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderMapper).insert(cap.capture());
        assertEquals(1, cap.getValue().getAutoAuthorized());
        assertEquals(0, cap.getValue().getAuthOverLimit());
        verify(approvalGateway, org.mockito.Mockito.never()).create(any());
    }
}
