package com.dzgylxt.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dzgylxt.common.BizException;
import com.dzgylxt.entity.budget.BudgetHeader;
import com.dzgylxt.entity.budget.BudgetLine;
import com.dzgylxt.entity.budget.BudgetOccupyLog;
import com.dzgylxt.entity.contract.Contract;
import com.dzgylxt.entity.order.OrderItem;
import com.dzgylxt.entity.order.PurchaseOrder;
import com.dzgylxt.enums.BudgetAction;
import com.dzgylxt.enums.BudgetBizType;
import com.dzgylxt.enums.BudgetHeaderStatus;
import com.dzgylxt.enums.ContractStatus;
import com.dzgylxt.enums.ItemType;
import com.dzgylxt.enums.OrderStatus;
import com.dzgylxt.mapper.budget.BudgetHeaderMapper;
import com.dzgylxt.mapper.budget.BudgetLineMapper;
import com.dzgylxt.mapper.budget.BudgetOccupyLogMapper;
import com.dzgylxt.mapper.contract.ContractMapper;
import com.dzgylxt.mapper.order.OrderItemMapper;
import com.dzgylxt.mapper.order.PurchaseOrderMapper;
import com.dzgylxt.service.IBudgetOccupyService;
import com.dzgylxt.service.IOrderService;
import com.dzgylxt.vo.budget.BudgetOccupyCmd;
import com.dzgylxt.vo.budget.OccupyResultVO;
import com.dzgylxt.vo.order.OrderChangeReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B7 预算生命周期真实 DB 集成测试（P2b-9 制度化）。
 *
 * <p>背景：P2b-9 双重取反缺陷 mock 全绿、运行态炸——mock 测不出 sum 口径类缺陷，
 * 本类用 Testcontainers 真实 MySQL8（行锁/version/CAS 真实语义）+ 真实 Redis
 * （RedisLockUtil 分布式锁）驱动 {@link IBudgetOccupyService} 真实实现，
 * 每条用例直查 budget_line / budget_occupy_log 断言落账（全程不 mock budgetOccupyService）。</p>
 *
 * <p>口径锚点（P2b-9 定稿）：流水 amount 一律存正数（方向由 action + balance 前后快照承载）；
 * 恒等式 {@code Σlog(OCCUPY−RELEASE) == budget_line.used_amount}（WRITE_OFF 不参与）。</p>
 *
 * <p>schema 初始化走应用自身 spring.sql.init（mode=always，classpath:db/schema.sql
 * + data.sql，continue-on-error=true 吸收存量库 ALTER 段）——与 QA 运行态验证同一条
 * 初始化链路，不另建初始化路径。</p>
 */
@SpringBootTest
@Testcontainers
class BudgetLifecycleIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("scm_it").withUsername("it").withPassword("it");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.0"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void containerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl()
                + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true");
        registry.add("spring.datasource.username", () -> "it");
        registry.add("spring.datasource.password", () -> "it");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private IBudgetOccupyService budgetOccupyService;

    @Autowired
    private IOrderService orderService;

    @Autowired
    private BudgetHeaderMapper budgetHeaderMapper;

    @Autowired
    private BudgetLineMapper budgetLineMapper;

    @Autowired
    private BudgetOccupyLogMapper occupyLogMapper;

    @Autowired
    private ContractMapper contractMapper;

    @Autowired
    private PurchaseOrderMapper purchaseOrderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private DataSource dataSource;

    // ---------------- 用例 1：提交即占 → 驳回释放 → 改额重提 ----------------

    /**
     * P2b-9 主场景：占用/释放/重占全链真实落账——终态 used_amount == 当前有效占用
     * （非叠加）；流水序列 OCCUPY(+10560)/RELEASE(+10560 正数)/OCCUPY(+5280)，
     * 每步守恒 Σlog(占用−释放) == used_amount。
     */
    @Test
    void occupy_release_reoccupy_lifecycle() {
        long lineId = seedLine(9100L, 9200L, 9, new BigDecimal("20000"));
        long biz = 71001L;

        // ① 提交即占 10560
        OccupyResult r1 = occupy(lineId, 9100L, 9200L, 9, BudgetBizType.APPLY, biz, "10560");
        assertTrue(r1.available, "足额占用成功");
        assertEquals(0, r1.used.compareTo(new BigDecimal("10560")), "used=10560");
        assertEquals(1, r1.version, "乐观锁 version 递增（种子 0 → 1）");
        List<BudgetOccupyLog> logs1 = logsOf(lineId, bizTypeCode(BudgetBizType.APPLY), biz);
        assertEquals(1, logs1.size());
        assertLog(logs1.get(0), BudgetAction.OCCUPY, "10560.00", "0", "10560");
        assertConserved(lineId, "占用后");

        // ② 驳回释放 10560——P2b-9：RELEASE 落正数（写侧禁负值）
        OccupyResult r2 = release(lineId, 9100L, BudgetBizType.APPLY, biz, "10560");
        assertTrue(r2.available);
        assertEquals(0, r2.used.compareTo(BigDecimal.ZERO), "释放后 used=0");
        List<BudgetOccupyLog> logs2 = logsOf(lineId, bizTypeCode(BudgetBizType.APPLY), biz);
        assertEquals(2, logs2.size());
        assertLog(logs2.get(1), BudgetAction.RELEASE, "10560.00", "10560", "0");
        assertTrue(logs2.get(1).getAmount().compareTo(BigDecimal.ZERO) > 0,
                "P2b-9：RELEASE 落正数，方向由 action+balance 快照承载");
        assertConserved(lineId, "释放后");

        // ③ 改额重提 5280——终态 used == 当前有效占用（非叠加）
        OccupyResult r3 = occupy(lineId, 9100L, 9200L, 9, BudgetBizType.APPLY, biz, "5280");
        assertTrue(r3.available);
        assertEquals(0, r3.used.compareTo(new BigDecimal("5280")), "重占后 used=5280（非 10560+5280）");
        assertConserved(lineId, "重占后");

        // 流水序列：OCCUPY(+10560)/RELEASE(+10560)/OCCUPY(+5280)
        List<BudgetOccupyLog> all = logsOf(lineId, bizTypeCode(BudgetBizType.APPLY), biz);
        assertEquals(List.of(BudgetAction.OCCUPY, BudgetAction.RELEASE, BudgetAction.OCCUPY),
                all.stream().map(BudgetOccupyLog::getAction).toList(), "流水动作序列");
        assertEquals(List.of("10560.00", "10560.00", "5280.00"),
                all.stream().map(l -> l.getAmount().toPlainString()).toList(),
                "P2b-9：amount 恒为正数序列");
    }

    // ---------------- 用例 2：全库守恒不变式 ----------------

    /** 多 biz 共享一行：每步后 Σlog(占用−释放) == used_amount（P2b-9 核心不变式）。 */
    @Test
    void conservation_holds_across_bizs_and_steps() {
        long lineId = seedLine(9101L, 9201L, 9, new BigDecimal("10000"));

        occupy(lineId, 9101L, 9201L, 9, BudgetBizType.APPLY, 72001L, "3000");
        assertConserved(lineId, "bizA 占用后");

        occupy(lineId, 9101L, 9201L, 9, BudgetBizType.ORDER, 72002L, "4000");
        assertConserved(lineId, "bizB 占用后");

        release(lineId, 9101L, BudgetBizType.APPLY, 72001L, "3000");
        assertConserved(lineId, "bizA 释放后");

        // 部分释放 bizB：1000 释放，3000 保留
        release(lineId, 9101L, BudgetBizType.ORDER, 72002L, "1000");
        assertConserved(lineId, "bizB 部分释放后");
        BudgetLine line = budgetLineMapper.selectById(lineId);
        assertEquals(0, line.getUsedAmount().compareTo(new BigDecimal("3000")),
                "终态 used = 4000 − 1000 = 3000");
    }

    // ---------------- 用例 3：占用 → 核销（writeOff） ----------------

    /** 核销落正流水、used 不变（核销=转移语义）；核销后该 biz 余额清零。 */
    @Test
    void writeOff_keepsUsed_andLogsPositive() {
        long lineId = seedLine(9102L, 9202L, 9, new BigDecimal("5000"));
        long biz = 73001L;

        occupy(lineId, 9102L, 9202L, 9, BudgetBizType.ORDER, biz, "3000");
        BigDecimal usedBefore = budgetLineMapper.selectById(lineId).getUsedAmount();

        OccupyResult r = writeOff(lineId, 9102L, BudgetBizType.ORDER, biz, "3000");
        assertTrue(r.available, "核销成功");

        BudgetLine line = budgetLineMapper.selectById(lineId);
        assertEquals(0, line.getUsedAmount().compareTo(usedBefore),
                "核销后 used_amount 不变（构成转移）");

        List<BudgetOccupyLog> logs = logsOf(lineId, bizTypeCode(BudgetBizType.ORDER), biz);
        BudgetOccupyLog wo = logs.get(logs.size() - 1);
        assertLog(wo, BudgetAction.WRITE_OFF, "3000.00", "3000", "3000");
        assertEquals(0, wo.getBalanceBefore().compareTo(wo.getBalanceAfter()),
                "核销流水 balance 前后相等");

        // 核销后该 biz 占用余额清零（sumBizOccupied 对 action=2 取负）
        assertEquals(0, budgetOccupyService.bizOccupied(lineId, BudgetBizType.ORDER, biz)
                .compareTo(BigDecimal.ZERO), "核销后 biz 占用余额=0");
        assertConserved(lineId, "核销后（WRITE_OFF 不参与恒等式）");
    }

    // ---------------- 用例 4：并发穿透 ----------------

    /**
     * 同部门×科目×月两线程并发占用 8000（行额度 10000）：Redis 锁串行化 + 行锁/version
     * 真实 CAS 下恰一方成功、另一方被余额拦截；账目守恒。两连接走独立事务与连接池。
     */
    @Test
    void concurrentOccupy_exactlyOneWins_andConserved() throws Exception {
        long lineId = seedLine(9103L, 9203L, 9, new BigDecimal("10000"));
        int threads = 2;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<OccupyResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final long biz = 74001L + i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await(5, TimeUnit.SECONDS);
                try {
                    return occupy(lineId, 9103L, 9203L, 9, BudgetBizType.ORDER, biz, "8000");
                } catch (BizException e) {
                    // Redis 锁 3s 快速失败等运营态拒绝亦视为未成功（不产生占用）
                    OccupyResult r = new OccupyResult();
                    r.available = false;
                    return r;
                }
            }));
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        go.countDown();
        int wins = 0;
        for (Future<OccupyResult> f : futures) {
            if (f.get(15, TimeUnit.SECONDS).available) {
                wins++;
            }
        }
        pool.shutdownNow();

        assertEquals(1, wins, "并发穿透：恰一方成功");
        BudgetLine line = budgetLineMapper.selectById(lineId);
        assertEquals(0, line.getUsedAmount().compareTo(new BigDecimal("8000")),
                "成功方占用 8000（余额不足方零写入）");
        assertConserved(lineId, "并发后守恒");
    }

    // ---------------- 用例 5：B8 错误分类真 DB 版 ----------------

    /**
     * 真实 CAS 下复现 B8 两类失败（updateUsedWithRetry 分类契约）：
     * 守卫失败（负 delta 超余额，version 未变）→ 3000 BIZ_ERROR；
     * version 确有变化且重试仍守卫失败 → 3002 DATA_CONFLICT。
     * 经 ReflectionTestUtils 调用私有分类方法（AopTestUtils 解包 CGLIB 代理取真实 target，
     * 避免私有方法读到代理实例未注入字段）——CAS SQL/余额守卫/version 比对均为真实 DB 语义。
     */
    @Test
    void casClassification_guardBizError_andVersionConflict3002() throws Exception {
        Object occupyTarget = org.springframework.aop.framework.AopProxyUtils
                .getSingletonTarget(budgetOccupyService);
        long lineId = seedLine(9104L, 9204L, 9, new BigDecimal("10000"));
        // 直改 used=100（模拟历史占用存量），version 保持种子值 0
        bumpUsed(lineId, new BigDecimal("100"), 0);

        // ① 守卫失败 → BIZ_ERROR(3000)：fresh 读（version=0），delta=-150 超余额，
        //    changeUsedAmount 守卫拒写且 version 未变 → 业务语义错误（非 3002）
        BudgetLine fresh = budgetLineMapper.selectById(lineId);
        BizException guard = assertThrows(BizException.class, () -> ReflectionTestUtils.invokeMethod(
                occupyTarget, "updateUsedWithRetry", fresh, new BigDecimal("-150")));
        assertEquals(3000, guard.getCode(), "守卫失败报 BIZ_ERROR 3000");
        BudgetLine afterGuard = budgetLineMapper.selectById(lineId);
        assertEquals(0, afterGuard.getUsedAmount().compareTo(new BigDecimal("100")), "守卫拒绝零写入");
        assertEquals(0, afterGuard.getVersion(), "守卫失败 version 不变");

        // ② version 冲突 + 重试仍守卫失败 → 3002 DATA_CONFLICT：
        //    持 stale 快照(version=0)，他连接并发推进 version=0→1（真实 CAS 竞争），
        //    重试仍因余额守卫失败 → 3002
        BudgetLine stale = budgetLineMapper.selectById(lineId);
        bumpUsed(lineId, new BigDecimal("100"), 1); // 并发写：version 0→1
        BizException conflict = assertThrows(BizException.class, () -> ReflectionTestUtils.invokeMethod(
                occupyTarget, "updateUsedWithRetry", stale, new BigDecimal("-150")));
        assertEquals(3002, conflict.getCode(), "version 冲突重试仍失败报 3002");
        BudgetLine afterConflict = budgetLineMapper.selectById(lineId);
        assertEquals(0, afterConflict.getUsedAmount().compareTo(new BigDecimal("100")), "冲突路径零写入");
        assertEquals(1, afterConflict.getVersion(), "冲突路径 version 保持并发写后的 1");
    }

    // ---------------- 用例 6：B6 运行态（无锚订单减额） ----------------

    /** B6：无锚订单（D2 手填合同，无申请/award 锚点）减额变更后 budget_occupied == 0（不为负）。 */
    @Test
    void noAnchorOrder_decrease_keepsBudgetOccupiedZero() {
        // 无锚合同：awardId=null
        Contract contract = new Contract();
        contract.setId(95001L);
        contract.setSupplierId(96001L);
        contract.setAwardId(null);
        contract.setNo("HT-IT-0001");
        contract.setTitle("IT-无锚合同");
        contract.setContractType(0);
        contract.setAmount(new BigDecimal("10000"));
        contract.setAvailableAmount(new BigDecimal("10000"));
        contract.setValidFrom(LocalDate.now().minusDays(1));
        contract.setValidTo(LocalDate.now().plusDays(30));
        contract.setStatus(ContractStatus.EFFECTIVE);
        contract.setVersion(0);
        contractMapper.insert(contract);

        PurchaseOrder order = new PurchaseOrder();
        order.setId(97001L);
        order.setContractId(95001L);
        order.setApplyId(null);
        order.setSupplierId(96001L);
        order.setOrderNo("DD-IT-0001");
        order.setOrderType(ItemType.MATERIAL);
        order.setStatus(OrderStatus.CREATED);
        order.setBudgetOccupied(BigDecimal.ZERO);
        purchaseOrderMapper.insert(order);

        OrderItem item = new OrderItem();
        item.setId(98001L);
        item.setOrderId(97001L);
        item.setSkuId(99001L);
        item.setQtyPurchase(new BigDecimal("10"));
        item.setQtyBase(new BigDecimal("10"));
        item.setConvSnapshot("{\"rate\":1}");
        item.setPrice(new BigDecimal("100"));
        orderItemMapper.insert(item);

        // 减额变更：10 → 5（amountDelta = -500），无锚订单 release 对零余额优雅跳过
        OrderChangeReqVO req = new OrderChangeReqVO();
        req.setReason("IT-减额变更");
        OrderChangeReqVO.ItemChange change = new OrderChangeReqVO.ItemChange();
        change.setOrderItemId(98001L);
        change.setNewQty(new BigDecimal("5"));
        req.getItems().add(change);

        orderService.changeOrder(97001L, req);

        PurchaseOrder after = purchaseOrderMapper.selectById(97001L);
        assertEquals(0, after.getBudgetOccupied().compareTo(BigDecimal.ZERO),
                "B6：无锚订单减额变更后 budget_occupied == 0（不得落负值）");
        Contract contractAfter = contractMapper.selectById(95001L);
        assertEquals(0, contractAfter.getAvailableAmount().compareTo(new BigDecimal("10500")),
                "减额回冲合同可用额度 10000+500=10500");
        OrderItem itemAfter = orderItemMapper.selectById(98001L);
        assertEquals(0, itemAfter.getQtyPurchase().compareTo(new BigDecimal("5")), "明细数量已变更");
    }

    // ---------------- fixtures & helpers ----------------

    /** 种子：预算头 + 单月度行（id 显式、version=0），返回行 id。 */
    private long seedLine(long deptId, long subjectId, int period, BigDecimal amount) {
        BudgetHeader header = new BudgetHeader();
        header.setId(90000L + deptId);
        header.setYear(2026);
        header.setDeptId(deptId);
        header.setTotalAmount(amount);
        header.setStatus(BudgetHeaderStatus.ACTIVE);
        budgetHeaderMapper.insert(header);

        BudgetLine line = new BudgetLine();
        line.setId(92000L + subjectId);
        line.setHeaderId(header.getId());
        line.setSubjectId(subjectId);
        line.setPeriod(period);
        line.setAmount(amount);
        line.setUsedAmount(BigDecimal.ZERO);
        line.setVersion(0);
        budgetLineMapper.insert(line);
        return line.getId();
    }

    private BudgetOccupyCmd cmd(long deptId, Long subjectId, Integer period,
                                BudgetBizType bizType, long bizId, String amount) {
        BudgetOccupyCmd c = new BudgetOccupyCmd();
        c.setDeptId(deptId);
        c.setYear(2026);
        c.setSubjectId(subjectId);
        c.setPeriod(period);
        c.setAmount(new BigDecimal(amount));
        c.setBizType(bizType);
        c.setBizId(bizId);
        c.setRemark("IT-" + bizType + ":" + bizId);
        return c;
    }

    /** 占用（返回值含 DB 直读的 used/version，杜绝 mock 假绿）。 */
    private OccupyResult occupy(long lineId, long deptId, Long subjectId, Integer period,
                                BudgetBizType bizType, long bizId, String amount) {
        OccupyResultVO vo = budgetOccupyService.occupy(
                cmd(deptId, subjectId, period, bizType, bizId, amount));
        return snapshot(vo, lineId);
    }

    /** 释放（deptId 供 Redis 锁键；lineId 用于回读落账）。 */
    private OccupyResult release(long lineId, long deptId, BudgetBizType bizType,
                                 long bizId, String amount) {
        BudgetOccupyCmd c = new BudgetOccupyCmd();
        c.setDeptId(deptId);
        c.setAmount(new BigDecimal(amount));
        c.setBizType(bizType);
        c.setBizId(bizId);
        c.setRemark("IT-release-" + bizId);
        return snapshot(budgetOccupyService.release(c), lineId);
    }

    /** 核销（used_amount 不变语义；lineId 用于回读落账）。 */
    private OccupyResult writeOff(long lineId, long deptId, BudgetBizType bizType,
                                  long bizId, String amount) {
        BudgetOccupyCmd c = new BudgetOccupyCmd();
        c.setDeptId(deptId);
        c.setAmount(new BigDecimal(amount));
        c.setBizType(bizType);
        c.setBizId(bizId);
        c.setRemark("IT-writeOff-" + bizId);
        return snapshot(budgetOccupyService.writeOff(c), lineId);
    }

    /** 结果快照：available 取自返回 VO；used/version 直查 DB。 */
    private OccupyResult snapshot(OccupyResultVO vo, Long lineId) {
        OccupyResult r = new OccupyResult();
        r.available = vo.isAvailable();
        if (lineId != null) {
            BudgetLine line = budgetLineMapper.selectById(lineId);
            r.used = line.getUsedAmount() == null ? BigDecimal.ZERO : line.getUsedAmount();
            r.version = line.getVersion();
        }
        return r;
    }

    /** 断言：Σlog(占用−释放) == budget_line.used_amount（P2b-9 核心不变式）。 */
    private void assertConserved(long lineId, String at) {
        BudgetLine line = budgetLineMapper.selectById(lineId);
        BigDecimal net = occupyLogMapper.sumNetOccupiedByLine(lineId);
        BigDecimal used = line.getUsedAmount() == null ? BigDecimal.ZERO : line.getUsedAmount();
        assertEquals(0, net.compareTo(used),
                at + "：Σlog(占用−释放)=" + net + " 应 == used_amount=" + used);
    }

    /** 某行+某 biz 的全部流水（按 id 升序）。 */
    private List<BudgetOccupyLog> logsOf(long lineId, int bizTypeCode, long bizId) {
        return occupyLogMapper.selectList(new LambdaQueryWrapper<BudgetOccupyLog>()
                .eq(BudgetOccupyLog::getBudgetLineId, lineId)
                .eq(BudgetOccupyLog::getBizType, bizTypeCode)
                .eq(BudgetOccupyLog::getBizId, bizId)
                .orderByAsc(BudgetOccupyLog::getId));
    }

    /** 断言单笔流水：动作 + 正数金额 + balance 前后快照。 */
    private void assertLog(BudgetOccupyLog log, BudgetAction action,
                           String amount, String before, String after) {
        assertEquals(action, log.getAction(), "流水动作");
        assertEquals(0, log.getAmount().compareTo(new BigDecimal(amount)),
                "流水金额（恒正数口径）");
        assertEquals(0, log.getBalanceBefore().compareTo(new BigDecimal(before)), "balance_before 快照");
        assertEquals(0, log.getBalanceAfter().compareTo(new BigDecimal(after)), "balance_after 快照");
    }

    /** 直改 used_amount/version（绕过服务，模拟存量数据或并发写方）。 */
    private void bumpUsed(long lineId, BigDecimal used, int version) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE budget_line SET used_amount = ?, version = ? WHERE id = ?")) {
            ps.setBigDecimal(1, used);
            ps.setInt(2, version);
            ps.setLong(3, lineId);
            assertEquals(1, ps.executeUpdate(), "直改受影响 1 行");
        }
    }

    /** bizType 的 DB 存储码（biz_type 列为 tinyint，IEnum 写入）。 */
    private int bizTypeCode(BudgetBizType type) {
        return type.getValue();
    }

    /** 用例内部结果载体（used/version 从 DB 直读，杜绝 mock 假绿）。 */
    private static final class OccupyResult {
        boolean available;
        BigDecimal used;
        Integer version;
    }
}
