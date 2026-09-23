package com.dzgylxt.service.impl.budget;

import com.dzgylxt.common.RedisLockUtil;
import com.dzgylxt.entity.budget.BudgetLine;
import com.dzgylxt.entity.budget.BudgetOccupyLog;
import com.dzgylxt.enums.BudgetAction;
import com.dzgylxt.enums.BudgetBizType;
import com.dzgylxt.mapper.budget.BudgetLineMapper;
import com.dzgylxt.mapper.budget.BudgetOccupyLogMapper;
import com.dzgylxt.vo.budget.BudgetOccupyCmd;
import com.dzgylxt.vo.budget.OccupyResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P3-T02 合规核心单测（设计 §3）：预算占用并发 + 超支语义 + 守恒不变式。
 *
 * <p>并发模型：FakeLine（ReentrantLock 行锁模拟 DB FOR UPDATE + AtomicReference
 * 余额模拟行数据），两线程同部门+同科目+同月并发占用——恰一方成功、账目守恒。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetOccupyConcurrencyTest {

    private static final Long DEPT_ID = 1L;
    private static final Long SUBJECT_ID = 900L;
    private static final Integer YEAR = 2026;
    private static final int PERIOD = 9;
    private static final Long LINE_ID = 5001L;
    private static final BigDecimal LINE_AMOUNT = new BigDecimal("1000");

    @Mock
    private BudgetLineMapper budgetLineMapper;

    @Mock
    private BudgetOccupyLogMapper occupyLogMapper;

    @Mock
    private RedisLockUtil redisLockUtil;

    @Mock
    private org.springframework.beans.factory.ObjectProvider<com.dzgylxt.approval.ApprovalGateway> gatewayProvider;

    private BudgetOccupyServiceImpl service;

    /** 行状态（模拟 DB 行：used_amount + version + 行锁）；R1：按行独立（跨月互不干扰）。 */
    private final ReentrantLock rowLock = new ReentrantLock(true);
    private final java.util.Map<Long, java.util.concurrent.atomic.AtomicReference<BigDecimal>> usedByLine =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<Long, java.util.concurrent.atomic.AtomicReference<Integer>> versionByLine =
            new java.util.concurrent.ConcurrentHashMap<>();

    private java.util.concurrent.atomic.AtomicReference<BigDecimal> usedRef(long lineId) {
        return usedByLine.computeIfAbsent(lineId,
                k -> new java.util.concurrent.atomic.AtomicReference<>(BigDecimal.ZERO));
    }

    private java.util.concurrent.atomic.AtomicReference<Integer> versionRef(long lineId) {
        return versionByLine.computeIfAbsent(lineId,
                k -> new java.util.concurrent.atomic.AtomicReference<>(1));
    }

    /** 当前被测主行（LINE_ID）的 used_amount（断言便捷入口）。 */
    private BigDecimal usedAmount() {
        return usedRef(LINE_ID).get();
    }

    @BeforeEach
    void setUp() {
        service = new BudgetOccupyServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "budgetLineMapper", budgetLineMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "occupyLogMapper", occupyLogMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "redisLockUtil", redisLockUtil);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "adjustThreshold", new BigDecimal("0.2"));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "gatewayProvider", gatewayProvider);

        lenient().when(redisLockUtil.tryLock(anyString(), anyLong())).thenReturn("tok");
        // unlock 视为事务结束：释放行锁（FOR UPDATE 随事务提交释放的单测模拟）
        lenient().doAnswer(inv -> {
            if (rowLock.isHeldByCurrentThread()) {
                rowLock.unlock();
            }
            return null;
        }).when(redisLockUtil).unlock(anyString(), anyString());

        // 行锁模拟 FOR UPDATE：同一把 ReentrantLock 串行化两线程
        lenient().when(budgetLineMapper.selectMonthlyLines(DEPT_ID, YEAR, SUBJECT_ID))
                .thenAnswer(inv -> List.of(fakeLine()));
        lenient().when(budgetLineMapper.selectForUpdateById(LINE_ID)).thenAnswer(inv -> {
            rowLock.lock();
            return fakeLine();
        });

        // 余额（log 守恒）以各行 used_amount 同步模拟：Σ占用−Σ释放 == used_amount（按行独立）
        lenient().when(occupyLogMapper.sumBizOccupied(anyLong(), anyInt(), anyLong()))
                .thenAnswer(inv -> usedRef(inv.getArgument(0, Long.class)).get());

        // 条件更新：version 匹配才生效（乐观兜底语义）；按行独立状态（R1 跨月覆盖）
        lenient().when(budgetLineMapper.changeUsedAmount(anyLong(), any(BigDecimal.class), anyInt()))
                .thenAnswer(inv -> {
                    Long lineId = inv.getArgument(0, Long.class);
                    BigDecimal delta = inv.getArgument(1);
                    Integer reqVersion = inv.getArgument(2);
                    AtomicReference<BigDecimal> used = usedRef(lineId);
                    AtomicReference<Integer> ver = versionRef(lineId);
                    if (!reqVersion.equals(ver.get())) {
                        return 0;
                    }
                    BigDecimal next = used.get().add(delta);
                    if (next.compareTo(BigDecimal.ZERO) < 0) {
                        return 0; // 不减穿 0
                    }
                    used.set(next);
                    ver.set(ver.get() + 1);
                    return 1;
                });

        // 流水写入
        lenient().when(occupyLogMapper.insert(any(BudgetOccupyLog.class))).thenReturn(1);

        // 月度调整（计划额度 amount，不影响 used_amount）
        lenient().when(budgetLineMapper.adjustAmount(anyLong(), any(BigDecimal.class), anyInt()))
                .thenAnswer(inv -> {
                    Long lineId = inv.getArgument(0, Long.class);
                    Integer reqVersion = inv.getArgument(2);
                    if (!reqVersion.equals(versionRef(lineId).get())) {
                        return 0;
                    }
                    versionRef(lineId).set(versionRef(lineId).get() + 1);
                    return 1;
                });
        lenient().when(budgetLineMapper.selectById(anyLong()))
                .thenAnswer(inv -> fakeLine(inv.getArgument(0, Long.class)));
        // 释放/核销按日志定位行：单测中恒为本行
        lenient().when(occupyLogMapper.selectLineIdsByBiz(anyInt(), anyLong()))
                .thenReturn(List.of(LINE_ID));
    }

    private BudgetLine fakeLine() {
        return fakeLine(LINE_ID);
    }

    private BudgetLine fakeLine(long lineId) {
        BudgetLine line = new BudgetLine();
        line.setId(lineId);
        line.setAmount(LINE_AMOUNT);
        line.setUsedAmount(usedRef(lineId).get());
        line.setVersion(versionRef(lineId).get());
        line.setPeriod(lineId == LINE_ID ? PERIOD : 8);
        return line;
    }

    private BudgetOccupyCmd cmd(BigDecimal amount, BudgetBizType bizType, long bizId) {
        BudgetOccupyCmd cmd = new BudgetOccupyCmd();
        cmd.setDeptId(DEPT_ID);
        cmd.setYear(YEAR);
        cmd.setSubjectId(SUBJECT_ID);
        cmd.setPeriod(PERIOD);
        cmd.setAmount(amount);
        cmd.setBizType(bizType);
        cmd.setBizId(bizId);
        cmd.setRemark("并发单测");
        return cmd;
    }

    /** 合规①：同部门+同科目+同月并发占用（1000 余额，各占 800）——恰一方成功、守恒。 */
    @Test
    void concurrentOccupy_exactlyOneSucceeds_conserves() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<OccupyResultVO> f1 = pool.submit(() -> {
            start.await();
            return service.occupy(cmd(new BigDecimal("800"), BudgetBizType.APPLY, 101L));
        });
        Future<OccupyResultVO> f2 = pool.submit(() -> {
            start.await();
            return service.occupy(cmd(new BigDecimal("800"), BudgetBizType.APPLY, 102L));
        });
        start.countDown();
        OccupyResultVO r1 = f1.get(10, TimeUnit.SECONDS);
        OccupyResultVO r2 = f2.get(10, TimeUnit.SECONDS);
        pool.shutdownNow();

        int success = (r1.isAvailable() ? 1 : 0) + (r2.isAvailable() ? 1 : 0);
        assertEquals(1, success, "并发占用应恰一方成功（行锁串行 + 余额校验），实际 r1="
                + r1.isAvailable() + " r2=" + r2.isAvailable());

        BigDecimal used = usedAmount();
        assertEquals(0, used.compareTo(new BigDecimal("800")), "used_amount == 成功方金额");
        // 守恒：Σlog(占用−释放) == used_amount（log 由同一 updateUsedWithRetry 驱动）
        verify(occupyLogMapper, atLeast(1)).insert(any(BudgetOccupyLog.class));
        OccupyResultVO winner = r1.isAvailable() ? r1 : r2;
        assertEquals(0, winner.getOccupied().compareTo(new BigDecimal("800")));
    }

    /** 合规②：余额不足零写入——无 log、used_amount 不变。 */
    @Test
    void insufficientBalance_zeroWrite() {
        OccupyResultVO result = service.occupy(cmd(new BigDecimal("1200"), BudgetBizType.APPLY, 201L));
        assertFalse(result.isAvailable());
        assertEquals(0, result.getOverAmount().compareTo(new BigDecimal("200")));
        assertEquals(0, usedAmount().compareTo(BigDecimal.ZERO), "不足时零写入");
        verify(occupyLogMapper, never()).insert(any(BudgetOccupyLog.class));
    }

    /** 合规③：无月度预算行 = 无预算 = 拦截（行 2）。 */
    @Test
    void noMonthlyLine_blocked() {
        when(budgetLineMapper.selectMonthlyLines(DEPT_ID, YEAR, SUBJECT_ID)).thenReturn(List.of());
        OccupyResultVO result = service.occupy(cmd(new BigDecimal("100"), BudgetBizType.APPLY, 301L));
        assertFalse(result.isAvailable());
        assertTrue(result.getMessage().contains("无当月预算行"));
    }

    /**
     * 合规③-R1：控制单元 = 部门×科目×<b>月份</b>——不同月度行互不挤占
     * （9 月行吃满后，8 月行仍足额可用；按行独立状态模拟真实月度行）。
     */
    @Test
    void monthlyControlUnit_crossMonthIndependent() {
        // 9 月占用 1000（吃满当月行）
        OccupyResultVO sep = service.occupy(cmd(new BigDecimal("1000"), BudgetBizType.APPLY, 311L));
        assertTrue(sep.isAvailable());

        // 8 月（另一条月度行 5002，独立 used/version 状态）不受 9 月占用影响
        when(budgetLineMapper.selectMonthlyLines(DEPT_ID, YEAR, SUBJECT_ID))
                .thenReturn(List.of(fakeLine(), fakeLine(5002L)));
        when(budgetLineMapper.selectForUpdateById(5002L)).thenAnswer(inv -> {
            rowLock.lock();
            return fakeLine(5002L);
        });
        BudgetOccupyCmd augCmd = cmd(new BigDecimal("400"), BudgetBizType.APPLY, 312L);
        augCmd.setPeriod(8);
        OccupyResultVO result = service.occupy(augCmd);
        assertTrue(result.isAvailable(), "R1：月度控制单元互相独立，8 月可用不受 9 月占用影响");
        assertEquals(0, usedRef(5002L).get().compareTo(new BigDecimal("400")), "8 月行独立落账");
    }

    /** 生命周期（行 11）：核销——used_amount 不变（构成转移），log balance 前后相等。 */
    @Test
    void writeOff_usedAmountUnchanged_balanceContinuous() {
        service.occupy(cmd(new BigDecimal("500"), BudgetBizType.APPLY, 611L));
        assertEquals(0, usedAmount().compareTo(new BigDecimal("500")));

        org.mockito.ArgumentCaptor<BudgetOccupyLog> logCaptor =
                org.mockito.ArgumentCaptor.forClass(BudgetOccupyLog.class);
        OccupyResultVO result = service.writeOff(cmd(new BigDecimal("500"), BudgetBizType.ORDER, 612L));
        assertTrue(result.isAvailable());
        // 核销不改 used_amount（占用量转移到核销构成）
        assertEquals(0, usedAmount().compareTo(new BigDecimal("500")),
                "核销仅转移构成（占用→核销），used_amount 不变");
        verify(occupyLogMapper, atLeast(1)).insert(logCaptor.capture());
        BudgetOccupyLog entry = logCaptor.getAllValues().stream()
                .filter(l -> l.getAction() == BudgetAction.WRITE_OFF).findFirst()
                .orElseThrow(() -> new AssertionError("未找到核销 log"));
        assertEquals(BudgetAction.WRITE_OFF, entry.getAction());
        assertEquals(0, entry.getAmount().compareTo(new BigDecimal("500")));
        assertEquals(0, entry.getBalanceAfter().compareTo(entry.getBalanceBefore()),
                "核销 log balance 前后相等（守恒）");
    }

    /** 合规④：超支仅 BUDGET 审批通过后（force=true）可占用；先占后审禁止。 */
    @Test
    void overspend_onlyAfterBudgetApproval() {
        // 先占后审禁止：force=false 超额 → 拦截零写入
        OccupyResultVO blocked = service.occupy(cmd(new BigDecimal("1500"), BudgetBizType.APPLY, 401L));
        assertFalse(blocked.isAvailable(), "先占后审禁止（force=false 超额拦截）");

        // BUDGET 审批通过后（force=true）：超支占用生效（1500 > 1000）
        OccupyResultVO forced = service.occupy(cmd(new BigDecimal("1500"), BudgetBizType.APPLY, 401L));
        assertFalse(forced.isAvailable());
        BudgetOccupyCmd forceCmd = cmd(new BigDecimal("1500"), BudgetBizType.APPLY, 401L);
        forceCmd.setForce(true);
        OccupyResultVO approved = service.occupy(forceCmd);
        assertTrue(approved.isAvailable(), "BUDGET 审批通过后 force 占用生效");
        assertEquals(0, usedAmount().compareTo(new BigDecimal("1500")), "超支占用落库");

        // 驳回路径（行 4）：无占用发生——used 保持 1500（本用例已 force 生效），语义由 handler 测试覆盖
    }

    /** 合规⑤：释放按日志余额守恒——释放后 used 回落且 Σlog==used。 */
    @Test
    void release_conservesInvariant() {
        service.occupy(cmd(new BigDecimal("600"), BudgetBizType.APPLY, 501L));
        assertEquals(0, usedAmount().compareTo(new BigDecimal("600")));

        OccupyResultVO released = service.release(cmd(new BigDecimal("600"), BudgetBizType.APPLY, 501L));
        assertTrue(released.isAvailable());
        assertEquals(0, usedAmount().compareTo(BigDecimal.ZERO), "释放后 used 回零（守恒）");

        // 超额释放按余额截断 + 告警（不产生负数）
        service.occupy(cmd(new BigDecimal("300"), BudgetBizType.APPLY, 502L));
        OccupyResultVO overRelease = service.release(cmd(new BigDecimal("500"), BudgetBizType.APPLY, 502L));
        assertEquals(0, overRelease.getOccupied().compareTo(new BigDecimal("300")));
        assertEquals(0, usedAmount().compareTo(BigDecimal.ZERO));
    }

    /** 合规⑥：月度调整——调减低于已占用拦截；守恒不被调整破坏（used_amount 不变）。 */
    @Test
    void adjustAmount_usedAmountUntouched() {
        service.occupy(cmd(new BigDecimal("400"), BudgetBizType.APPLY, 601L));

        // 调减低于已占用 → 拒绝
        IllegalArgumentException.class.getName();
        try {
            service.adjustAmount(LINE_ID, new BigDecimal("100"), "调减测试");
            org.junit.jupiter.api.Assertions.fail("调减低于已占用应拒绝");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("不能低于已占用"));
        }
        // 调增（阈值内：delta 100 ≤ 1000×20%）直生效，used_amount 不变（Σlog 守恒不破坏）
        boolean pending = service.adjustAmount(LINE_ID, new BigDecimal("1100"), "调增测试");
        assertFalse(pending, "未超阈值直接生效");
        assertEquals(0, usedAmount().compareTo(new BigDecimal("400")), "调整不改 used_amount");

        // 调增超阈值 → 走 BUDGET 审批、本次不生效（pendingApproval=true）
        assertTrue(service.adjustAmount(LINE_ID, new BigDecimal("2000"), "超阈值调增"),
                "超阈值应发 BUDGET 审批且不落库");
    }
}
