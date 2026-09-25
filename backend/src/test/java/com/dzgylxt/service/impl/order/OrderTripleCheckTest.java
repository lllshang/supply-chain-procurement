package com.dzgylxt.service.impl.order;

import com.dzgylxt.common.BizException;
import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.common.RedisLockUtil;
import com.dzgylxt.entity.contract.Contract;
import com.dzgylxt.entity.contract.ContractPriceItem;
import com.dzgylxt.entity.order.OrderItem;
import com.dzgylxt.entity.purchase.PurchaseApply;
import com.dzgylxt.entity.purchase.PurchaseApplyItem;
import com.dzgylxt.enums.ContractStatus;
import com.dzgylxt.enums.ItemType;
import com.dzgylxt.enums.OrderStatus;
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
import com.dzgylxt.vo.order.OrderChangeReqVO;
import com.dzgylxt.vo.order.OrderCreateReqVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 下单三重校验事务专项测试（P2-T06/T10，设计 §4 合规硬规则）。
 *
 * <p><b>并发单测</b>：两线程并发对同一合同/同一申请明细下单——按行锁语义串行后，
 * 第二单必须因「不超合同额度 / 不超申请余量」条件失败（一方成功一方失败）。
 * 单测内以 {@link FakeRow}（ReentrantLock + 条件 SQL 语义）模拟 DB 行锁与
 * 条件 UPDATE 的原子性；真实 FOR UPDATE 由运行时冒烟覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderTripleCheckTest {

    private static final long CONTRACT_ID = 500L;
    private static final long APPLY_ID = 600L;
    private static final long APPLY_ITEM_ID = 601L;

    /** 合同行/申请明细行的伪 DB 行：模拟 FOR UPDATE 串行 + 条件 UPDATE 原子性。 */
    static class FakeRow {
        final ReentrantLock lock = new ReentrantLock(true);
        BigDecimal availableAmount;
        BigDecimal remainQty;
        int version;
    }

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
    @Mock
    private com.dzgylxt.mapper.approval.ApprovalTaskMapper approvalTaskMapper;

    private FakeRow contractRow;
    private FakeRow itemRow;

    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "contractMapper", contractMapper);
        // P3c-A1：默认空清单=免价格校验（存量/框架合同语义）；个别用例自行覆盖
        when(contractPriceItemMapper.selectByContract(any(Long.class))).thenReturn(java.util.List.of());
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
        // P3 预算占用：转移/释放直接成功（本测试聚焦合同/余量三重校验）
        ReflectionTestUtils.setField(service, "budgetOccupyService", budgetOccupyService);
        lenient().when(budgetOccupyService.transfer(any(com.dzgylxt.vo.budget.BudgetTransferCmd.class)))
                .thenReturn(com.dzgylxt.vo.budget.OccupyResultVO.ok(BigDecimal.ZERO, List.of()));
        lenient().when(budgetOccupyService.release(any(com.dzgylxt.vo.budget.BudgetOccupyCmd.class)))
                .thenReturn(com.dzgylxt.vo.budget.OccupyResultVO.ok(BigDecimal.ZERO, List.of()));
        lenient().when(budgetOccupyService.occupiedTotal(any(), any()))
                .thenReturn(BigDecimal.ZERO);
        // IService.save 走 baseMapper.insert
        ReflectionTestUtils.setField(service, "baseMapper", purchaseOrderMapper);
        lenient().when(purchaseOrderMapper.insert(any(com.dzgylxt.entity.order.PurchaseOrder.class)))
                .thenReturn(1);

        // 伪行状态：合同可用额度 1000；申请余量（基本单位）100
        contractRow = new FakeRow();
        contractRow.availableAmount = new BigDecimal("1000");
        contractRow.version = 0;
        itemRow = new FakeRow();
        itemRow.remainQty = new BigDecimal("100");
        itemRow.version = 0;

        // Redis 锁直接放行（多实例层在本测试非考察点）
        lenient().when(redisLockUtil.tryLock(any(), anyLong())).thenReturn("token");
        lenient().doNothing().when(redisLockUtil).unlock(any(), any());

        stubContractMapper();
        stubApplyItemMapper();
        stubUnitConversion();
    }

    private void stubContractMapper() {
        // selectForUpdate = 行锁 + 读
        when(contractMapper.selectForUpdate(CONTRACT_ID)).thenAnswer(inv -> {
            contractRow.lock.lock();
            try {
                Contract c = baseContract();
                c.setAvailableAmount(contractRow.availableAmount);
                c.setVersion(contractRow.version);
                return c;
            } finally {
                contractRow.lock.unlock();
            }
        });
        // deductAvailable：行锁内执行条件 UPDATE（version + available >= delta 原子）
        when(contractMapper.deductAvailable(eq(CONTRACT_ID), any(BigDecimal.class), any(Integer.class)))
                .thenAnswer((Answer<Integer>) inv -> {
                    BigDecimal delta = inv.getArgument(1);
                    Integer version = inv.getArgument(2);
                    contractRow.lock.lock();
                    try {
                        if (contractRow.version != version
                                || contractRow.availableAmount.compareTo(delta) < 0) {
                            return 0;
                        }
                        contractRow.availableAmount = contractRow.availableAmount.subtract(delta);
                        contractRow.version++;
                        return 1;
                    } finally {
                        contractRow.lock.unlock();
                    }
                });
        when(contractMapper.selectById(CONTRACT_ID)).thenAnswer(inv -> {
            Contract c = baseContract();
            c.setAvailableAmount(contractRow.availableAmount);
            c.setVersion(contractRow.version);
            return c;
        });
        lenient().when(contractMapper.updateById(any(Contract.class))).thenReturn(1);
    }

    private void stubApplyItemMapper() {
        // selectForUpdateByIds = 行锁 + 读（本测试单明细）
        when(applyItemMapper.selectForUpdateByIds(anyList())).thenAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            itemRow.lock.lock();
            try {
                PurchaseApplyItem item = baseApplyItem();
                item.setRemainQty(itemRow.remainQty);
                item.setVersion(itemRow.version);
                return List.of(item);
            } finally {
                itemRow.lock.unlock();
            }
        });
        // deductRemain：行锁内条件 UPDATE（version + remain >= qty 原子）
        when(applyItemMapper.deductRemain(eq(APPLY_ITEM_ID), any(BigDecimal.class), any(Integer.class)))
                .thenAnswer((Answer<Integer>) inv -> {
                    BigDecimal qty = inv.getArgument(1);
                    Integer version = inv.getArgument(2);
                    itemRow.lock.lock();
                    try {
                        if (itemRow.version != version || itemRow.remainQty.compareTo(qty) < 0) {
                            return 0;
                        }
                        itemRow.remainQty = itemRow.remainQty.subtract(qty);
                        itemRow.version++;
                        return 1;
                    } finally {
                        itemRow.lock.unlock();
                    }
                });
        when(applyItemMapper.selectById(APPLY_ITEM_ID)).thenAnswer(inv -> {
            PurchaseApplyItem item = baseApplyItem();
            item.setRemainQty(itemRow.remainQty);
            item.setVersion(itemRow.version);
            return item;
        });
        when(applyMapper.selectById(APPLY_ID)).thenAnswer(inv -> {
            PurchaseApply apply = new PurchaseApply();
            apply.setId(APPLY_ID);
            apply.setStatus(PurchaseApplyStatus.APPROVED);
            return apply;
        });
        lenient().when(applyItemMapper.selectList(any())).thenAnswer(inv -> {
            PurchaseApplyItem item = baseApplyItem();
            item.setRemainQty(itemRow.remainQty);
            return List.of(item);
        });
        lenient().when(applyMapper.updateById(any(PurchaseApply.class))).thenReturn(1);
    }

    private void stubUnitConversion() {
        com.dzgylxt.entity.catalog.UnitConversion conv = new com.dzgylxt.entity.catalog.UnitConversion();
        conv.setRate(BigDecimal.ONE);
        lenient().when(unitConversionMapper.selectCurrentEffective(anyLong(), any(), any()))
                .thenReturn(conv);
    }

    private Contract baseContract() {
        Contract c = new Contract();
        c.setId(CONTRACT_ID);
        c.setStatus(ContractStatus.EFFECTIVE);
        c.setAmount(new BigDecimal("1000"));
        c.setValidFrom(LocalDate.now().minusDays(1));
        c.setValidTo(LocalDate.now().plusDays(30));
        return c;
    }

    private PurchaseApplyItem baseApplyItem() {
        PurchaseApplyItem item = new PurchaseApplyItem();
        item.setId(APPLY_ITEM_ID);
        item.setApplyId(APPLY_ID);
        item.setSkuId(9L);
        item.setItemType(ItemType.MATERIAL);
        item.setPurchaseUnit("PCS");
        item.setApplyQty(new BigDecimal("100"));
        item.setOrderedQty(new BigDecimal("100").subtract(itemRow.remainQty));
        return item;
    }

    /** 下单请求：数量（基本单位）× 单价 = 金额。 */
    private OrderCreateReqVO createReq(BigDecimal qty, BigDecimal price) {
        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setContractId(CONTRACT_ID);
        req.setApplyId(APPLY_ID);
        OrderCreateReqVO.OrderItemReqVO item = new OrderCreateReqVO.OrderItemReqVO();
        item.setApplyItemId(APPLY_ITEM_ID);
        item.setSkuId(9L);
        item.setQty(qty);
        item.setPurchaseUnit("PCS");
        item.setPrice(price);
        req.setItems(List.of(item));
        return req;
    }

    /** 规则①③：正常下单成功，额度与余量扣减正确。 */
    @Test
    void createOrder_normalPath_passes() {
        List<Long> ids = service.createOrder(createReq(new BigDecimal("10"), new BigDecimal("10")));

        assertEquals(1, ids.size());
        assertEquals(0, contractRow.availableAmount.compareTo(new BigDecimal("900")),
                "扣减式额度：1000 - 100 = 900");
        assertEquals(0, itemRow.remainQty.compareTo(new BigDecimal("90")),
                "余量：100 - 10 = 90");
    }

    /** 规则②：单笔超额被拒。 */
    @Test
    void createOrder_overContractAmount_rejected() {
        var e = org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> service.createOrder(createReq(new BigDecimal("100"), new BigDecimal("11"))));
        assertTrue(e.getMessage().contains("超出合同可用额度"));
        assertEquals(0, contractRow.availableAmount.compareTo(new BigDecimal("1000")),
                "失败不扣额度");
        // 注：真实事务中已扣余量也会随事务回滚（@Transactional rollbackFor=Exception），
        // 此处 mock 层不模拟回滚，仅校验额度未被扣减。
    }

    /** 规则③：单笔超余量被拒。 */
    @Test
    void createOrder_overApplyRemain_rejected() {
        var e = org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> service.createOrder(createReq(new BigDecimal("101"), BigDecimal.ONE)));
        assertTrue(e.getMessage().contains("超出申请余量"));
    }

    // ---------------- P3c-A1：第四重校验（合同价格清单一致性，BR-26 硬拦截） ----------------

    private ContractPriceItem priceRow(Long id, Long skuId, BigDecimal price) {
        ContractPriceItem row = new ContractPriceItem();
        row.setId(id);
        row.setContractId(CONTRACT_ID);
        row.setSkuId(skuId);
        row.setUnitPrice(price);
        row.setSourceType(2);
        return row;
    }

    /** AC②：价格偏离（本单 598 vs 清单 568）→ 拦截不落库，额度不扣。 */
    @Test
    void createOrder_priceMismatchContractList_rejected() {
        when(contractPriceItemMapper.selectByContract(CONTRACT_ID))
                .thenReturn(List.of(priceRow(7001L, 9L, new BigDecimal("568"))));

        var e = org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> service.createOrder(createReq(new BigDecimal("10"), new BigDecimal("598"))));
        assertTrue(e.getMessage().contains("与合同价格清单不一致"), "实际：" + e.getMessage());
        assertEquals(0, contractRow.availableAmount.compareTo(new BigDecimal("1000")),
                "价格拦截先于额度扣减，额度未动");
    }

    /** SKU 不在清单中 → 拦截。 */
    @Test
    void createOrder_skuNotInContractList_rejected() {
        when(contractPriceItemMapper.selectByContract(CONTRACT_ID))
                .thenReturn(List.of(priceRow(7002L, 9999L, new BigDecimal("10"))));

        var e = org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> service.createOrder(createReq(new BigDecimal("10"), new BigDecimal("10"))));
        assertTrue(e.getMessage().contains("不在合同价格清单中"));
    }

    /** AC①：价格与清单一致 → 成功，且 contract_item_id 回填到 order_item。 */
    @Test
    void createOrder_priceMatchesContractList_passesAndBackfills() {
        when(contractPriceItemMapper.selectByContract(CONTRACT_ID))
                .thenReturn(List.of(priceRow(7003L, 9L, new BigDecimal("10"))));

        List<Long> ids = service.createOrder(createReq(new BigDecimal("10"), new BigDecimal("10")));
        assertEquals(1, ids.size());

        org.mockito.ArgumentCaptor<OrderItem> captor =
                org.mockito.ArgumentCaptor.forClass(OrderItem.class);
        org.mockito.Mockito.verify(orderItemMapper).insert(captor.capture());
        assertEquals(7003L, captor.getValue().getContractItemId(), "回填命中的清单行ID");
    }

    /** AC③：合同无清单记录（框架/存量）→ 免检，下单正常走额度闸。 */
    @Test
    void createOrder_noContractPriceList_skipsPriceCheck() {
        when(contractPriceItemMapper.selectByContract(CONTRACT_ID)).thenReturn(List.of());

        List<Long> ids = service.createOrder(createReq(new BigDecimal("10"), new BigDecimal("50")));
        assertEquals(1, ids.size(), "无清单合同免价格校验（与清单价 568 无关，50 亦放行）");
        assertEquals(0, contractRow.availableAmount.compareTo(new BigDecimal("500")),
                "额度闸照常扣减：1000 - 10×50 = 500");
    }

    /** 规则①：非 EFFECTIVE 合同拒绝。 */
    @Test
    void createOrder_contractInvalid_rejected() {
        when(contractMapper.selectForUpdate(CONTRACT_ID)).thenAnswer(inv -> {
            Contract c = baseContract();
            c.setStatus(ContractStatus.TERMINATED);
            return c;
        });
        var e = org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> service.createOrder(createReq(BigDecimal.ONE, BigDecimal.ONE)));
        assertTrue(e.getMessage().contains("合同未生效"));
    }

    /**
     * <b>并发单测（合规重点）</b>：两线程并发下单（各 60 × 10 = 600，额度 1000 时
     * 第二单 600 会失败于额度；余量 100 时第二单 60 会失败于余量）。
     * 行锁语义（FakeRow 串行）下必有一方失败，且账目守恒。
     */
    @Test
    void createOrder_concurrent_twoThreads_oneFailsAndBooksConserved() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Boolean> f1 = pool.submit(() -> {
            start.await();
            try {
                service.createOrder(createReq(new BigDecimal("60"), new BigDecimal("10")));
                return true;
            } catch (BizException e) {
                return false;
            }
        });
        Future<Boolean> f2 = pool.submit(() -> {
            start.await();
            try {
                service.createOrder(createReq(new BigDecimal("60"), new BigDecimal("10")));
                return true;
            } catch (BizException e) {
                return false;
            }
        });
        Future<Boolean> first = f1;
        start.countDown();
        boolean r1 = first.get(10, TimeUnit.SECONDS);
        boolean r2 = f2.get(10, TimeUnit.SECONDS);
        pool.shutdownNow();

        // 恰好一单成功（600 ≤ 1000 额度但 60+60 > 100 余量 → 第二单失败于余量）
        org.junit.jupiter.api.Assertions.assertNotEquals(r1, r2, "并发下必须一方失败一方成功");
        // 账目守恒：成功一单扣 60 余量 / 600 额度
        assertEquals(0, itemRow.remainQty.compareTo(new BigDecimal("40")));
        assertEquals(0, contractRow.availableAmount.compareTo(new BigDecimal("400")));
        assertTrue(r1 || r2);
    }

    // ---------------- #48 申请头状态闸门 ----------------

    /** #48：作废（CLOSED）/驳回等非有效状态申请不可下单。 */
    @Test
    void createOrder_closedApply_rejected() {
        PurchaseApply closed = new PurchaseApply();
        closed.setId(APPLY_ID);
        closed.setStatus(PurchaseApplyStatus.CLOSED);
        when(applyMapper.selectById(APPLY_ID)).thenReturn(closed);
        var e = org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> service.createOrder(createReq(BigDecimal.ONE, BigDecimal.ONE)));
        assertTrue(e.getMessage().contains("申请状态不允许下单"), e.getMessage());
    }

    /** #48：驳回（REJECTED）申请不可下单。 */
    @Test
    void createOrder_rejectedApply_rejected() {
        PurchaseApply rejected = new PurchaseApply();
        rejected.setId(APPLY_ID);
        rejected.setStatus(PurchaseApplyStatus.REJECTED);
        when(applyMapper.selectById(APPLY_ID)).thenReturn(rejected);
        var e = org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> service.createOrder(createReq(BigDecimal.ONE, BigDecimal.ONE)));
        assertTrue(e.getMessage().contains("申请状态不允许下单"), e.getMessage());
    }

    // ---------------- #47 变更增额预算拦截升级 ----------------

    /** #47：变更增额被预算拦截 → 拦截 + 生成 BUDGET 升级审批任务（非纯拦截）。 */
    @Test
    void changeOrder_budgetBlocked_createsBudgetUpgradeTask() {
        com.dzgylxt.approval.ApprovalGateway gateway =
                org.mockito.Mockito.mock(com.dzgylxt.approval.ApprovalGateway.class);
        ReflectionTestUtils.setField(service, "approvalGateway", gateway);

        // 订单：CREATED、budgetOccupied=600、apply 上下文齐全
        com.dzgylxt.entity.order.PurchaseOrder order = new com.dzgylxt.entity.order.PurchaseOrder();
        order.setId(700L);
        order.setOrderNo("DD-TEST-000070");
        order.setContractId(CONTRACT_ID);
        order.setApplyId(APPLY_ID);
        order.setStatus(OrderStatus.CREATED);
        order.setBudgetOccupied(new BigDecimal("600"));
        when(purchaseOrderMapper.selectById(700L)).thenReturn(order);

        com.dzgylxt.entity.order.OrderItem oi = new com.dzgylxt.entity.order.OrderItem();
        oi.setId(4001L);
        oi.setOrderId(700L);
        oi.setSkuId(9L);
        oi.setApplyItemId(APPLY_ITEM_ID);
        oi.setPrice(new BigDecimal("10"));
        oi.setQtyPurchase(new BigDecimal("10"));
        oi.setQtyBase(new BigDecimal("10"));
        oi.setConvSnapshot("{\"rate\":1}");
        when(orderItemMapper.selectList(any())).thenReturn(List.of(oi));

        PurchaseApply apply = new PurchaseApply();
        apply.setId(APPLY_ID);
        apply.setStatus(PurchaseApplyStatus.PARTIAL_ORDER);
        apply.setDeptId(1L);
        when(applyMapper.selectById(APPLY_ID)).thenReturn(apply);

        // 变更余量充足（40），仅预算拦截
        itemRow.remainQty = new BigDecimal("40");
        when(budgetOccupyService.occupy(any(com.dzgylxt.vo.budget.BudgetOccupyCmd.class)))
                .thenReturn(com.dzgylxt.vo.budget.OccupyResultVO.blocked(
                        new BigDecimal("400"), new BigDecimal("100"), "当月预算余额不足"));

        com.dzgylxt.vo.order.OrderChangeReqVO req = new com.dzgylxt.vo.order.OrderChangeReqVO();
        com.dzgylxt.vo.order.OrderChangeReqVO.ItemChange change =
                new com.dzgylxt.vo.order.OrderChangeReqVO.ItemChange();
        change.setOrderItemId(4001L);
        change.setNewQty(new BigDecimal("20"));
        req.setItems(List.of(change));
        req.setReason("增量变更");

        var e = org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                () -> service.changeOrder(700L, req));
        assertTrue(e.getMessage().contains("已生成 BUDGET 升级审批"), e.getMessage());
        // 升级任务已生成（bizType=BUDGET，payload 带 orderChange 标记）
        org.mockito.ArgumentCaptor<com.dzgylxt.approval.ApprovalTaskSpec> spec =
                org.mockito.ArgumentCaptor.forClass(com.dzgylxt.approval.ApprovalTaskSpec.class);
        org.mockito.Mockito.verify(gateway).create(spec.capture());
        assertEquals("BUDGET", spec.getValue().getBizType());
        assertTrue(spec.getValue().getPayloadJson().contains("orderChange"),
                "payload 应带 orderChange 升级标记");
    }

    // ---------------- P2b-5：无申请来源订单变更——锚点回填 + 硬控 ----------------

    /** P2b-8③：无申请来源（D9 锚点）订单变更超额 → 预算拦截 + BUDGET 升级任务（含科目锚点）。 */
    @Test
    void changeOrder_noApply_awardAnchor_overspend_blockedAndEscalated() {
        ReflectionTestUtils.setField(service, "approvalGateway", approvalGateway);
        ReflectionTestUtils.setField(service, "approvalTaskMapper", approvalTaskMapper);
        when(approvalTaskMapper.selectList(any())).thenReturn(List.of());
        when(approvalGateway.create(any(com.dzgylxt.approval.ApprovalTaskSpec.class))).thenReturn(1L);

        com.dzgylxt.entity.order.PurchaseOrder order = new com.dzgylxt.entity.order.PurchaseOrder();
        order.setId(710L);
        order.setOrderNo("DD-TEST-000071");
        order.setContractId(CONTRACT_ID);
        order.setApplyId(null);
        order.setStatus(OrderStatus.CREATED);
        order.setBudgetOccupied(new BigDecimal("2112"));
        when(purchaseOrderMapper.selectById(710L)).thenReturn(order);

        // 合同关联 D9 定标锚点（dept=1, subject=2）；合同额度放行，让请求触达预算闸
        Contract contract = baseContract();
        contract.setAwardId(810L);
        when(contractMapper.selectForUpdate(CONTRACT_ID)).thenReturn(contract);
        when(contractMapper.selectById(CONTRACT_ID)).thenReturn(contract);
        when(contractMapper.deductAvailable(eq(CONTRACT_ID), any(BigDecimal.class), any(Integer.class)))
                .thenReturn(1);
        com.dzgylxt.entity.purchase.Award award = new com.dzgylxt.entity.purchase.Award();
        award.setId(810L);
        award.setDeptId(1L);
        award.setSubjectId(2L);
        when(awardMapper.selectById(810L)).thenReturn(award);

        com.dzgylxt.entity.order.OrderItem oi = new com.dzgylxt.entity.order.OrderItem();
        oi.setId(4101L);
        oi.setOrderId(710L);
        oi.setSkuId(9L);
        oi.setApplyItemId(null);
        oi.setPrice(new BigDecimal("1056"));
        oi.setQtyPurchase(new BigDecimal("2"));
        oi.setQtyBase(new BigDecimal("2"));
        oi.setConvSnapshot("{\"rate\":1}");
        when(orderItemMapper.selectList(any())).thenReturn(List.of(oi));

        when(budgetOccupyService.occupy(any(com.dzgylxt.vo.budget.BudgetOccupyCmd.class)))
                .thenReturn(com.dzgylxt.vo.budget.OccupyResultVO.blocked(
                        new BigDecimal("1000"), new BigDecimal("49688"), "当月预算余额不足"));

        OrderChangeReqVO req = new OrderChangeReqVO();
        OrderChangeReqVO.ItemChange change = new OrderChangeReqVO.ItemChange();
        change.setOrderItemId(4101L);
        change.setNewQty(new BigDecimal("50"));
        req.setItems(List.of(change));
        req.setReason("增购");

        BizException e = assertThrows(BizException.class, () -> service.changeOrder(710L, req));
        assertTrue(e.getMessage().contains("超出预算余额"), e.getMessage());

        // 占用命令落到 award 锚点科目行（P2b-5 回填链）
        ArgumentCaptor<com.dzgylxt.vo.budget.BudgetOccupyCmd> cmdCap =
                ArgumentCaptor.forClass(com.dzgylxt.vo.budget.BudgetOccupyCmd.class);
        Mockito.verify(budgetOccupyService).occupy(cmdCap.capture());
        assertEquals(1L, cmdCap.getValue().getDeptId());
        assertEquals(2L, cmdCap.getValue().getSubjectId(), "锚点回填：award.dept/subject");
        // BUDGET 升级任务：bizId=订单 id（QA2-06），payload 带科目锚点
        ArgumentCaptor<com.dzgylxt.approval.ApprovalTaskSpec> specCap =
                ArgumentCaptor.forClass(com.dzgylxt.approval.ApprovalTaskSpec.class);
        Mockito.verify(approvalGateway).create(specCap.capture());
        assertEquals("BUDGET", specCap.getValue().getBizType());
        assertEquals(710L, specCap.getValue().getBizId());
        assertTrue(specCap.getValue().getPayloadJson().contains("\"subjectId\":2"),
                "升级 payload 必须带科目锚点，否则 force 占用将跨科目虚占");
    }

    /** P2b-5：无申请来源且合同未关联定标（无锚点）→ 变更增额硬控拦截（禁止静默绕过）。 */
    @Test
    void changeOrder_noAnchor_hardGateRejected() {
        com.dzgylxt.entity.order.PurchaseOrder order = new com.dzgylxt.entity.order.PurchaseOrder();
        order.setId(720L);
        order.setOrderNo("DD-TEST-000072");
        order.setContractId(CONTRACT_ID);
        order.setApplyId(null);
        order.setStatus(OrderStatus.CREATED);
        order.setBudgetOccupied(new BigDecimal("100"));
        when(purchaseOrderMapper.selectById(720L)).thenReturn(order);

        Contract contract = baseContract(); // awardId=null
        when(contractMapper.selectForUpdate(CONTRACT_ID)).thenReturn(contract);
        when(contractMapper.deductAvailable(eq(CONTRACT_ID), any(BigDecimal.class), any(Integer.class)))
                .thenReturn(1);

        com.dzgylxt.entity.order.OrderItem oi = new com.dzgylxt.entity.order.OrderItem();
        oi.setId(4201L);
        oi.setOrderId(720L);
        oi.setSkuId(9L);
        oi.setApplyItemId(null);
        oi.setPrice(new BigDecimal("10"));
        oi.setQtyPurchase(new BigDecimal("2"));
        oi.setQtyBase(new BigDecimal("2"));
        oi.setConvSnapshot("{\"rate\":1}");
        when(orderItemMapper.selectList(any())).thenReturn(List.of(oi));

        OrderChangeReqVO req = new OrderChangeReqVO();
        OrderChangeReqVO.ItemChange change = new OrderChangeReqVO.ItemChange();
        change.setOrderItemId(4201L);
        change.setNewQty(new BigDecimal("5"));
        req.setItems(List.of(change));
        req.setReason("增购");

        BizException e = assertThrows(BizException.class, () -> service.changeOrder(720L, req));
        assertTrue(e.getMessage().contains("无预算锚点"), e.getMessage());
        Mockito.verify(budgetOccupyService, Mockito.never())
                .occupy(any(com.dzgylxt.vo.budget.BudgetOccupyCmd.class));
    }

    // ---------------- P2b-6：无申请来源下单——占用落 ORDER 流水 / 无锚置 0 ----------------

    /** P2b-6：无申请来源（D9 锚点）下单 → AWARD→ORDER 同行转移落 ORDER 流水。 */
    @Test
    void createOrder_noApply_awardAnchor_transfersToOrderFlows() {
        Contract contract = baseContract();
        contract.setAwardId(810L);
        when(contractMapper.selectForUpdate(CONTRACT_ID)).thenReturn(contract);
        when(contractMapper.selectById(CONTRACT_ID)).thenReturn(contract);
        when(contractMapper.deductAvailable(eq(CONTRACT_ID), any(BigDecimal.class), any(Integer.class)))
                .thenReturn(1);

        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setContractId(CONTRACT_ID);
        req.setApplyId(null); // 无申请来源
        OrderCreateReqVO.OrderItemReqVO item = new OrderCreateReqVO.OrderItemReqVO();
        item.setSkuId(9L);
        item.setQty(BigDecimal.ONE);
        item.setPrice(BigDecimal.TEN);
        item.setPurchaseUnit("BOX");
        req.setItems(List.of(item));
        req.setSourceReason("无申请来源补录：框架合同直发");

        service.createOrder(req);

        ArgumentCaptor<com.dzgylxt.vo.budget.BudgetTransferCmd> cap =
                ArgumentCaptor.forClass(com.dzgylxt.vo.budget.BudgetTransferCmd.class);
        Mockito.verify(budgetOccupyService).transfer(cap.capture());
        assertEquals(com.dzgylxt.enums.BudgetBizType.AWARD, cap.getValue().getFromBizType());
        assertEquals(810L, cap.getValue().getFromBizId());
        assertEquals(com.dzgylxt.enums.BudgetBizType.ORDER, cap.getValue().getToBizType());
        assertEquals(0, cap.getValue().getAmount().compareTo(BigDecimal.TEN));
    }

    /** P2b-6：无锚订单（无申请且合同未关联定标）下单 → budget_occupied 置 0、无转移（不记假占用）。 */
    @Test
    void createOrder_noAnchor_budgetOccupiedZero() {
        when(contractMapper.selectForUpdate(CONTRACT_ID)).thenReturn(baseContract());
        when(contractMapper.deductAvailable(eq(CONTRACT_ID), any(BigDecimal.class), any(Integer.class)))
                .thenReturn(1);

        OrderCreateReqVO req = new OrderCreateReqVO();
        req.setContractId(CONTRACT_ID);
        req.setApplyId(null);
        OrderCreateReqVO.OrderItemReqVO item = new OrderCreateReqVO.OrderItemReqVO();
        item.setSkuId(9L);
        item.setQty(BigDecimal.ONE);
        item.setPrice(BigDecimal.TEN);
        item.setPurchaseUnit("BOX");
        req.setItems(List.of(item));
        req.setSourceReason("无锚订单：无申请且合同未关联定标");

        service.createOrder(req);

        ArgumentCaptor<com.dzgylxt.entity.order.PurchaseOrder> cap =
                ArgumentCaptor.forClass(com.dzgylxt.entity.order.PurchaseOrder.class);
        Mockito.verify(purchaseOrderMapper).insert(cap.capture());
        assertEquals(0, BigDecimal.ZERO.compareTo(cap.getValue().getBudgetOccupied()),
                "无锚订单不得记与预算系统脱钩的假占用");
        Mockito.verify(budgetOccupyService, Mockito.never())
                .transfer(any(com.dzgylxt.vo.budget.BudgetTransferCmd.class));
    }

    // ---------------- B6（P2b-13）：无锚订单减额——budget_occupied 下界保护 ----------------

    /** B6：无锚订单（D2）occupied=0 减额变更 → budget_occupied 保持 0（release 零余额跳过，不得落负值）。 */
    @Test
    void changeOrder_noAnchor_reduction_occupiedStaysZero() {
        com.dzgylxt.entity.order.PurchaseOrder order = new com.dzgylxt.entity.order.PurchaseOrder();
        order.setId(730L);
        order.setOrderNo("DD-TEST-000073");
        order.setContractId(CONTRACT_ID);
        order.setApplyId(null);
        order.setStatus(OrderStatus.CREATED);
        order.setBudgetOccupied(BigDecimal.ZERO);
        when(purchaseOrderMapper.selectById(730L)).thenReturn(order);

        Contract contract = baseContract(); // awardId=null → 无锚
        when(contractMapper.selectForUpdate(CONTRACT_ID)).thenReturn(contract);
        when(contractMapper.deductAvailable(eq(CONTRACT_ID), any(BigDecimal.class), any(Integer.class)))
                .thenReturn(1);

        com.dzgylxt.entity.order.OrderItem oi = new com.dzgylxt.entity.order.OrderItem();
        oi.setId(4301L);
        oi.setOrderId(730L);
        oi.setSkuId(9L);
        oi.setApplyItemId(null);
        oi.setPrice(new BigDecimal("10"));
        oi.setQtyPurchase(new BigDecimal("5"));
        oi.setQtyBase(new BigDecimal("5"));
        oi.setConvSnapshot("{\"rate\":1}");
        when(orderItemMapper.selectList(any())).thenReturn(List.of(oi));

        OrderChangeReqVO req = new OrderChangeReqVO();
        OrderChangeReqVO.ItemChange change = new OrderChangeReqVO.ItemChange();
        change.setOrderItemId(4301L);
        change.setNewQty(new BigDecimal("2")); // 减额：5 → 2
        req.setItems(List.of(change));
        req.setReason("减购");

        service.changeOrder(730L, req);

        assertEquals(0, order.getBudgetOccupied().compareTo(BigDecimal.ZERO),
                "无锚订单减额后 budget_occupied 应保持 0，不得落负值");
        Mockito.verify(budgetOccupyService).release(any(com.dzgylxt.vo.budget.BudgetOccupyCmd.class));
        Mockito.verify(budgetOccupyService, Mockito.never())
                .occupy(any(com.dzgylxt.vo.budget.BudgetOccupyCmd.class));
    }
}
