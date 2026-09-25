package com.dzgylxt.service.impl.order;

import com.dzgylxt.common.BizException;
import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.common.RedisLockUtil;
import com.dzgylxt.entity.contract.Contract;
import com.dzgylxt.entity.contract.ContractSkuWhitelist;
import com.dzgylxt.entity.order.PurchaseOrder;
import com.dzgylxt.entity.purchase.AwardItem;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * P4 D15 合同供货范围校验单测（设计 §5.4 方案 A；AC 对齐规格 §6）：
 * ①有价格清单 → 沿用 A1 硬拦截（行为回归零差异）；②无清单有定标 → SKU ∈ award_item 集；
 * ③皆无 → 白名单命中校验；空白名单 = 维持现网行为（额度闸兜底）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractSkuScopeTest {

    private static final long CONTRACT_ID = 500L;
    private static final long AWARD_ID = 800L;
    private static final long SKU_ID = 9L;

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

    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "contractMapper", contractMapper);
        // 关键前提：无价格清单（触发 D15 分支②③）
        when(contractPriceItemMapper.selectByContract(any(Long.class))).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "contractPriceItemMapper", contractPriceItemMapper);
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
        lenient().when(budgetOccupyService.occupiedTotal(any(), any())).thenReturn(BigDecimal.ZERO);
        ReflectionTestUtils.setField(service, "baseMapper", purchaseOrderMapper);
        lenient().when(purchaseOrderMapper.insert(any(PurchaseOrder.class))).thenReturn(1);
        lenient().when(redisLockUtil.tryLock(any(), anyLong())).thenReturn("token");
        lenient().doNothing().when(redisLockUtil).unlock(any(), any());

        when(contractMapper.selectForUpdate(CONTRACT_ID)).thenAnswer(inv -> baseContract());
        when(contractMapper.deductAvailable(eq(CONTRACT_ID), any(BigDecimal.class), any(Integer.class)))
                .thenReturn(1);
        when(contractMapper.selectById(CONTRACT_ID)).thenAnswer(inv -> baseContract());
        lenient().when(contractMapper.updateById(any(Contract.class))).thenReturn(1);

        com.dzgylxt.entity.catalog.UnitConversion conv = new com.dzgylxt.entity.catalog.UnitConversion();
        conv.setRate(BigDecimal.ONE);
        lenient().when(unitConversionMapper.selectCurrentEffective(anyLong(), any(), any())).thenReturn(conv);

        // D14：开启但阈值极大，避免 DAILY_AUTH 任务干扰本测试关注点
        ReflectionTestUtils.setField(service, "dailyAuthEnabled", true);
        ReflectionTestUtils.setField(service, "dailyAuthLimit", new BigDecimal("99999999"));

        setUser();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Contract baseContract() {
        Contract c = new Contract();
        c.setId(CONTRACT_ID);
        c.setStatus(ContractStatus.EFFECTIVE);
        c.setAmount(new BigDecimal("2000000"));
        c.setAvailableAmount(new BigDecimal("2000000"));
        c.setVersion(0);
        c.setValidFrom(LocalDate.now().minusDays(1));
        c.setValidTo(LocalDate.now().plusDays(30));
        c.setAwardId(null);
        return c;
    }

    private OrderCreateReqVO dailyReq() {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setContractId(CONTRACT_ID);
        req.setApplyId(null);
        OrderCreateReqVO.OrderItemReqVO item = new OrderCreateReqVO.OrderItemReqVO();
        item.setSkuId(SKU_ID);
        item.setQty(BigDecimal.ONE);
        item.setPurchaseUnit("PCS");
        item.setPrice(new BigDecimal("100"));
        item.setItemType(ItemType.MATERIAL);
        req.setItems(List.of(item));
        req.setSourceReason("日常补货");
        return req;
    }

    private void setUser() {
        LoginUser u = new LoginUser();
        u.setId(99L);
        u.setUsername("scopeuser");
        u.setMainDeptId(1L);
        u.setPerms(List.of("order:create"));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(u, null, List.of()));
    }

    private ContractSkuWhitelist whitelistRow(long skuId) {
        ContractSkuWhitelist row = new ContractSkuWhitelist();
        row.setContractId(CONTRACT_ID);
        row.setSkuId(skuId);
        return row;
    }

    // ==================== 分支③：无清单无定标 → 白名单 ====================

    @Test
    void whitelist_hit_passes() {
        when(contractSkuWhitelistMapper.selectList(any()))
                .thenReturn(List.of(whitelistRow(SKU_ID), whitelistRow(10L)));

        service.createOrder(dailyReq());
        // 无异常 = 白名单命中放行
    }

    @Test
    void whitelist_miss_rejected() {
        when(contractSkuWhitelistMapper.selectList(any()))
                .thenReturn(List.of(whitelistRow(10L), whitelistRow(11L)));

        BizException e = assertThrows(BizException.class, () -> service.createOrder(dailyReq()));
        assertEquals(4000, e.getCode());
        assertTrue(e.getMessage().contains("SKU 白名单"));
    }

    @Test
    void whitelist_empty_keepsCurrentBehavior() {
        // AC④：空白名单合同现网不变（额度闸兜底）
        when(contractSkuWhitelistMapper.selectList(any())).thenReturn(List.of());

        service.createOrder(dailyReq());
    }

    // ==================== 分支②：无清单有定标 → award_item SKU 集 ====================

    @Test
    void awardScope_hit_passes() {
        Contract c = baseContract();
        c.setAwardId(AWARD_ID);
        when(contractMapper.selectForUpdate(CONTRACT_ID)).thenReturn(c);
        when(contractMapper.selectById(CONTRACT_ID)).thenReturn(c);
        AwardItem ai = new AwardItem();
        ai.setAwardId(AWARD_ID);
        ai.setSkuId(SKU_ID);
        when(awardItemMapper.selectList(any())).thenReturn(List.of(ai));

        service.createOrder(dailyReq());
    }

    @Test
    void awardScope_miss_rejected() {
        Contract c = baseContract();
        c.setAwardId(AWARD_ID);
        when(contractMapper.selectForUpdate(CONTRACT_ID)).thenReturn(c);
        when(contractMapper.selectById(CONTRACT_ID)).thenReturn(c);
        AwardItem ai = new AwardItem();
        ai.setAwardId(AWARD_ID);
        ai.setSkuId(77L);
        when(awardItemMapper.selectList(any())).thenReturn(List.of(ai));

        BizException e = assertThrows(BizException.class, () -> service.createOrder(dailyReq()));
        assertEquals(4000, e.getCode());
        assertTrue(e.getMessage().contains("定标价格清单"));
    }
}
