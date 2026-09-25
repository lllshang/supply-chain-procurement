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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D14 日常采购「授权」控制专项测试（对应 docs/D14日常采购授权控制_规格设计.md）。
 *
 * <p>复用 OrderTripleCheckTest 的下单三重校验 mock 装配基座，仅将请求改为
 * <b>无申请来源（applyId=null）= 日常采购</b>，并注入 SecurityContext 验证授权留痕与超额度升级。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class D14AuthorizationTest {

    private static final long CONTRACT_ID = 500L;
    private static final long APPLY_ITEM_ID = 601L;

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

    private final BigDecimal AUTH_LIMIT = new BigDecimal("500000");

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
        // D14：审批网关 mock 注入（createDailyAuthTask 在 approvalGateway==null 时直接 return）
        ReflectionTestUtils.setField(service, "approvalGateway", approvalGateway);
        lenient().when(budgetOccupyService.transfer(any())).thenReturn(
                com.dzgylxt.vo.budget.OccupyResultVO.ok(BigDecimal.ZERO, List.of()));
        lenient().when(budgetOccupyService.release(any())).thenReturn(
                com.dzgylxt.vo.budget.OccupyResultVO.ok(BigDecimal.ZERO, List.of()));
        lenient().when(budgetOccupyService.occupiedTotal(any(), any())).thenReturn(BigDecimal.ZERO);

        ReflectionTestUtils.setField(service, "baseMapper", purchaseOrderMapper);
        lenient().when(purchaseOrderMapper.insert(any(PurchaseOrder.class))).thenReturn(1);

        // 合同可用额度放大到 200 万，使「超授权额度(50万)」测试不被合同闸拦截
        lenient().when(redisLockUtil.tryLock(any(), anyLong())).thenReturn("token");
        lenient().doNothing().when(redisLockUtil).unlock(any(), any());

        stubContractMapper();
        stubUnitConversion();

        // D14：默认开启 + 50 万阈值（与 @Value 默认值一致；单测未注入 Spring 取字段初始化值）
        ReflectionTestUtils.setField(service, "dailyAuthEnabled", true);
        ReflectionTestUtils.setField(service, "dailyAuthLimit", AUTH_LIMIT);
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

    /** 日常采购请求：无 applyId，金额 = qty × price。 */
    private OrderCreateReqVO dailyReq(BigDecimal qty, BigDecimal price) {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setContractId(CONTRACT_ID);
        req.setApplyId(null); // ← 日常采购：无申请来源
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
                .setAuthentication(new UsernamePasswordAuthenticationToken(u, null, java.util.List.of()));
    }

    @Test
    void dailyOrder_recordsAuthorizer_whenUserHasPerms() {
        setUser(true);
        service.createOrder(dailyReq(BigDecimal.ONE, new BigDecimal("100")));

        ArgumentCaptor<PurchaseOrder> cap = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderMapper).insert(cap.capture());
        PurchaseOrder saved = cap.getValue();
        assertEquals(99L, saved.getAuthorizedBy());
        assertEquals("authuser", saved.getAuthorizedName());
        assertNotNull(saved.getAuthorizedTime());
        assertEquals(0, saved.getAuthOverLimit());
    }

    @Test
    void dailyOrder_throwsWhenUserHasNoPerms() {
        setUser(false);
        BizException ex = assertThrows(BizException.class,
                () -> service.createOrder(dailyReq(BigDecimal.ONE, new BigDecimal("100"))));
        assertTrue(ex.getMessage().contains("无日常采购授权"), ex.getMessage());
    }

    @Test
    void dailyOrder_underLimit_noEscalation() {
        setUser(true);
        // 金额 10 < 50 万阈值
        service.createOrder(dailyReq(BigDecimal.ONE, new BigDecimal("10")));

        ArgumentCaptor<PurchaseOrder> cap = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderMapper).insert(cap.capture());
        assertEquals(0, cap.getValue().getAuthOverLimit());
        verify(approvalGateway, org.mockito.Mockito.never()).create(any());
    }

    @Test
    void dailyOrder_overLimit_createsDailyAuthTask_andMarksOverLimit() {
        setUser(true);
        // 金额 60 万 > 50 万阈值（合同可用 200 万，通过合同闸）
        service.createOrder(dailyReq(BigDecimal.ONE, new BigDecimal("600000")));

        ArgumentCaptor<PurchaseOrder> cap = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderMapper).insert(cap.capture());
        assertEquals(1, cap.getValue().getAuthOverLimit());

        verify(approvalGateway, org.mockito.Mockito.times(1)).create(argThat(
                spec -> "DAILY_AUTH".equals(spec.getBizType())
                        && spec.getTitle() != null
                        && spec.getTitle().contains("超授权额度")));
    }

    @Test
    void dailyOrder_noSecurityContext_doesNotThrow_andAuthorizerNull() {
        // 无安全上下文（兼容既有单测基线 / 内部调用）：放行但不记授权人
        SecurityContextHolder.clearContext();
        service.createOrder(dailyReq(BigDecimal.ONE, new BigDecimal("10")));

        ArgumentCaptor<PurchaseOrder> cap = ArgumentCaptor.forClass(PurchaseOrder.class);
        verify(purchaseOrderMapper).insert(cap.capture());
        assertNull(cap.getValue().getAuthorizedBy());
        assertNull(cap.getValue().getAuthorizedName());
        assertNotNull(cap.getValue().getAuthorizedTime());
    }
}
