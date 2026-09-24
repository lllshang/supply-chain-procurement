package com.dzgylxt.service.impl.budget;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dzgylxt.common.RedisLockUtil;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.common.BizException;
import com.dzgylxt.entity.approval.ApprovalTask;
import com.dzgylxt.entity.budget.BudgetLine;
import com.dzgylxt.entity.budget.BudgetOccupyLog;
import com.dzgylxt.enums.ApprovalStatus;
import com.dzgylxt.enums.BudgetAction;
import com.dzgylxt.enums.BudgetBizType;
import com.dzgylxt.mapper.approval.ApprovalTaskMapper;
import com.dzgylxt.mapper.budget.BudgetLineMapper;
import com.dzgylxt.mapper.budget.BudgetOccupyLogMapper;
import com.dzgylxt.security.UserContext;
import com.dzgylxt.service.IBudgetOccupyService;
import com.dzgylxt.vo.budget.BudgetOccupyCmd;
import com.dzgylxt.vo.budget.BudgetTransferCmd;
import com.dzgylxt.vo.budget.OccupyResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 预算占用/释放/核销/转移/调整唯一写入口实现（P3 设计 §2.3/§3，合规核心）。
 *
 * <p>事务方案（复用 P2 §4 基线）：</p>
 * <ol>
 *   <li>Redis 锁 {@code budget:lock:{deptId}:{subjectId}:{period}}（细到月，不同月不互阻，
 *       3s 快速失败）——多实例防穿透；</li>
 *   <li>{@code budget_line} 行锁 FOR UPDATE（按 {@code budget_line.id} 升序逐行加锁防死锁）——主锁；</li>
 *   <li>{@code changeUsedAmount} 带 {@code WHERE version=?}（乐观兜底，冲突重试 1 次）；</li>
 *   <li>校验、used_amount 更新、budget_occupy_log 写入同事务；每笔动作带 balance 前后快照。</li>
 * </ol>
 *
 * <p>两段式占用（先全量加锁校验、足额才写入）保证「余额不足零写入」（行 2 暂不占用语义）；
 * force 超支占用仅由 {@code com.dzgylxt.approval.BudgetApprovalHandler} 在 BUDGET
 * 审批通过后调用（先占后审禁止）。</p>
 */
@Service
public class BudgetOccupyServiceImpl implements IBudgetOccupyService {

    private static final Logger log = LoggerFactory.getLogger(BudgetOccupyServiceImpl.class);
    private static final long LOCK_WAIT_MILLIS = 3000L;

    @Autowired
    private BudgetLineMapper budgetLineMapper;

    @Autowired
    private BudgetOccupyLogMapper occupyLogMapper;

    @Autowired
    private RedisLockUtil redisLockUtil;

    @Autowired
    private ApprovalTaskMapper approvalTaskMapper;

    /** BUDGET 升级审批入口（ObjectProvider 防循环依赖：网关→回调→本服务）。 */
    @Autowired
    private org.springframework.beans.factory.ObjectProvider<com.dzgylxt.approval.ApprovalGateway> gatewayProvider;

    // ---------------- 占用 ----------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OccupyResultVO occupy(BudgetOccupyCmd cmd) {
        validateCmd(cmd);
        if (cmd.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "占用金额必须大于 0");
        }
        int year = cmd.getYear() == null ? LocalDate.now().getYear() : cmd.getYear();
        int period = resolvePeriod(cmd);
        String lockKey = "budget:lock:" + cmd.getDeptId() + ":" + cmd.getSubjectId() + ":" + period;
        String token = redisLockUtil.tryLock(lockKey, LOCK_WAIT_MILLIS);
        if (token == null) {
            throw new BizException(ResultCode.BIZ_ERROR, "预算处理繁忙，请稍后重试");
        }
        try {
            // ① 全量加锁（按 id 升序，防交叉死锁）+ 汇总可用余额
            List<BudgetLine> locked = lockMonthlyLines(cmd.getDeptId(), year, cmd.getSubjectId(), period);
            if (locked.isEmpty()) {
                // 月度行不存在 = 无预算 = 拦截（行 2）
                return OccupyResultVO.blocked(BigDecimal.ZERO, cmd.getAmount(),
                        "无当月预算行（部门 " + cmd.getDeptId() + " " + year + "年" + period + "月），需预算升级审批");
            }
            BigDecimal totalAvailable = BigDecimal.ZERO;
            for (BudgetLine line : locked) {
                totalAvailable = totalAvailable.add(availableOf(line));
            }
            // ② 足额校验（不足零写入，行 2：暂不占用）
            if (!cmd.isForce() && totalAvailable.compareTo(cmd.getAmount()) < 0) {
                return OccupyResultVO.blocked(totalAvailable, cmd.getAmount().subtract(totalAvailable),
                        "当月预算余额不足：可用 " + totalAvailable + "，需 " + cmd.getAmount());
            }
            // ③ 分摊写入（force 时末行兜底超支）
            return distribute(cmd, locked, cmd.getAmount(), cmd.isForce());
        } finally {
            redisLockUtil.unlock(lockKey, token);
        }
    }

    // ---------------- R7 只读再校验 ----------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OccupyResultVO checkOnly(BudgetOccupyCmd cmd) {
        validateCmd(cmd);
        if (cmd.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "校验金额必须大于 0");
        }
        int year = cmd.getYear() == null ? LocalDate.now().getYear() : cmd.getYear();
        int period = resolvePeriod(cmd);
        String lockKey = "budget:lock:" + cmd.getDeptId() + ":" + cmd.getSubjectId() + ":" + period;
        String token = redisLockUtil.tryLock(lockKey, LOCK_WAIT_MILLIS);
        if (token == null) {
            throw new BizException(ResultCode.BIZ_ERROR, "预算处理繁忙，请稍后重试");
        }
        try {
            // 与 occupy 同锁链（Redis 锁 + 行锁 FOR UPDATE），但只读：不写 used_amount、不写 log
            List<BudgetLine> locked = lockMonthlyLines(cmd.getDeptId(), year, cmd.getSubjectId(), period);
            if (locked.isEmpty()) {
                return OccupyResultVO.blocked(BigDecimal.ZERO, cmd.getAmount(),
                        "无当月预算行（部门 " + cmd.getDeptId() + " " + year + "年" + period + "月），需预算升级审批");
            }
            BigDecimal totalAvailable = BigDecimal.ZERO;
            for (BudgetLine line : locked) {
                totalAvailable = totalAvailable.add(availableOf(line));
            }
            if (totalAvailable.compareTo(cmd.getAmount()) < 0) {
                return OccupyResultVO.blocked(totalAvailable, cmd.getAmount().subtract(totalAvailable),
                        "当月预算余额不足：可用 " + totalAvailable + "，需 " + cmd.getAmount());
            }
            OccupyResultVO vo = OccupyResultVO.ok(BigDecimal.ZERO, locked.stream().map(BudgetLine::getId).toList());
            vo.setMessage("预算再校验通过（可用 " + totalAvailable + "，仅校验不占用）");
            return vo;
        } finally {
            redisLockUtil.unlock(lockKey, token);
        }
    }

    // ---------------- 释放 ----------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OccupyResultVO release(BudgetOccupyCmd cmd) {
        validateBiz(cmd);
        String lockKey = "budget:lock:" + cmd.getDeptId() + ":*:" + (cmd.getPeriod() == null ? 0 : cmd.getPeriod());
        String token = redisLockUtil.tryLock(lockKey, LOCK_WAIT_MILLIS);
        if (token == null) {
            throw new BizException(ResultCode.BIZ_ERROR, "预算处理繁忙，请稍后重试");
        }
        try {
            List<Long> lineIds = occupyLogMapper.selectLineIdsByBiz(cmd.getBizType().getValue(), cmd.getBizId());
            BigDecimal remaining = cmd.getAmount();
            BigDecimal released = BigDecimal.ZERO;
            for (Long lineId : lineIds) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                BudgetLine line = budgetLineMapper.selectForUpdateById(lineId);
                if (line == null) {
                    continue;
                }
                BigDecimal balance = occupyLogMapper.sumBizOccupied(lineId, cmd.getBizType().getValue(), cmd.getBizId());
                BigDecimal take = balance.min(remaining);
                if (take.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                BigDecimal[] snap = updateUsedWithRetry(line, take.negate());
                // P2b-9 方案 A 口径定稿：RELEASE 落负数（带符号流水，take.negate()），
                // 查询侧（sumBizOccupied/sumNetOccupiedByLine）对带符号 amount 原样求和——
                // 唯一禁改点：查询侧不得对 RELEASE 再取反（历史双重取反缺陷根因）
                writeLog(line, cmd, BudgetAction.RELEASE, take.negate(), snap[0], snap[1]);
                released = released.add(take);
                remaining = remaining.subtract(take);
            }
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                // 不足按余额释放并告警（设计 §2.3）
                log.warn("[预算释放] biz={}:{} 请求释放 {} 但余额仅释放 {}，差额 {} 未释放（按余额释放告警）",
                        cmd.getBizType(), cmd.getBizId(), cmd.getAmount(), released, remaining);
            }
            OccupyResultVO vo = OccupyResultVO.ok(released, lineIds);
            vo.setMessage(released.compareTo(cmd.getAmount()) < 0
                    ? "按余额释放 " + released + "（请求 " + cmd.getAmount() + "）" : "释放成功");
            return vo;
        } finally {
            redisLockUtil.unlock(lockKey, token);
        }
    }

    // ---------------- 核销 ----------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OccupyResultVO writeOff(BudgetOccupyCmd cmd) {
        validateBiz(cmd);
        String lockKey = "budget:lock:" + cmd.getDeptId() + ":*:" + (cmd.getPeriod() == null ? 0 : cmd.getPeriod());
        String token = redisLockUtil.tryLock(lockKey, LOCK_WAIT_MILLIS);
        if (token == null) {
            throw new BizException(ResultCode.BIZ_ERROR, "预算处理繁忙，请稍后重试");
        }
        try {
            List<Long> lineIds = occupyLogMapper.selectLineIdsByBiz(cmd.getBizType().getValue(), cmd.getBizId());
            BigDecimal remaining = cmd.getAmount();
            BigDecimal writtenOff = BigDecimal.ZERO;
            for (Long lineId : lineIds) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                BudgetLine line = budgetLineMapper.selectForUpdateById(lineId);
                if (line == null) {
                    continue;
                }
                BigDecimal balance = occupyLogMapper.sumBizOccupied(lineId, cmd.getBizType().getValue(), cmd.getBizId());
                BigDecimal take = balance.min(remaining);
                if (take.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                // 核销：used_amount 不变（构成转移），log balance 前后相等
                BigDecimal usedNow = line.getUsedAmount() == null ? BigDecimal.ZERO : line.getUsedAmount();
                writeLog(line, cmd, BudgetAction.WRITE_OFF, take, usedNow, usedNow);
                writtenOff = writtenOff.add(take);
                remaining = remaining.subtract(take);
            }
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                log.warn("[预算核销] biz={}:{} 请求核销 {} 仅核销 {}（占用余额不足）",
                        cmd.getBizType(), cmd.getBizId(), cmd.getAmount(), writtenOff);
            }
            OccupyResultVO vo = OccupyResultVO.ok(writtenOff, lineIds);
            vo.setMessage("核销成功（used_amount 不变，构成转移）");
            return vo;
        } finally {
            redisLockUtil.unlock(lockKey, token);
        }
    }

    // ---------------- 转移 ----------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OccupyResultVO transfer(BudgetTransferCmd cmd) {
        if (cmd == null || cmd.getFromBizType() == null || cmd.getFromBizId() == null
                || cmd.getToBizType() == null || cmd.getToBizId() == null
                || cmd.getAmount() == null || cmd.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "转移命令非法");
        }
        String token = redisLockUtil.tryLock("budget:transfer:" + cmd.getFromBizType() + ":" + cmd.getFromBizId(),
                LOCK_WAIT_MILLIS);
        if (token == null) {
            throw new BizException(ResultCode.BIZ_ERROR, "预算处理繁忙，请稍后重试");
        }
        try {
            List<Long> lineIds = occupyLogMapper.selectLineIdsByBiz(
                    cmd.getFromBizType().getValue(), cmd.getFromBizId());
            if (lineIds.isEmpty()) {
                // 源无占用（如历史数据）：目标侧直接按占用落账（无预算约束场景）
                log.warn("[预算转移] 源 biz={}:{} 无占用流水，跳过转移", cmd.getFromBizType(), cmd.getFromBizId());
                return OccupyResultVO.ok(BigDecimal.ZERO, lineIds);
            }
            BigDecimal remaining = cmd.getAmount();
            BigDecimal moved = BigDecimal.ZERO;
            for (Long lineId : lineIds) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                BudgetLine line = budgetLineMapper.selectForUpdateById(lineId);
                if (line == null) {
                    continue;
                }
                BigDecimal balance = occupyLogMapper.sumBizOccupied(lineId,
                        cmd.getFromBizType().getValue(), cmd.getFromBizId());
                BigDecimal move = balance.min(remaining);
                if (move.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                // 同一行：源释放 + 目标占用（净额 0，used_amount 不变，恒等式保持）
                BudgetOccupyCmd srcCmd = baseCmdOf(line);
                srcCmd.setBizType(cmd.getFromBizType());
                srcCmd.setBizId(cmd.getFromBizId());
                srcCmd.setRemark(cmd.getRemark() == null ? "转移出→" + cmd.getToBizType() + ":" + cmd.getToBizId()
                        : cmd.getRemark());
                BigDecimal[] srcSnap = updateUsedWithRetry(line, move.negate());
                // P2b-9 方案 A 口径：RELEASE 落负数（同 release()，查询侧带符号原样求和）
                writeLog(line, srcCmd, BudgetAction.RELEASE, move.negate(), srcSnap[0], srcSnap[1]);

                BudgetOccupyCmd dstCmd = baseCmdOf(line);
                dstCmd.setBizType(cmd.getToBizType());
                dstCmd.setBizId(cmd.getToBizId());
                dstCmd.setRemark(cmd.getRemark() == null ? "转移自←" + cmd.getFromBizType() + ":" + cmd.getFromBizId()
                        : cmd.getRemark());
                BigDecimal[] snap = updateUsedWithRetry(line, move);
                writeLog(line, dstCmd, BudgetAction.OCCUPY, move, snap[0], snap[1]);
                moved = moved.add(move);
                remaining = remaining.subtract(move);
            }
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                // 源余额不足（如单价上调）：差额在已触达行上强制占用（超支语义）
                log.warn("[预算转移] 源 biz={}:{} 余额不足，差额 {} 按超支占用",
                        cmd.getFromBizType(), cmd.getFromBizId(), remaining);
                BudgetOccupyCmd forceCmd = baseCmdOf(null);
                forceCmd.setDeptId(null);
                forceCmd.setBizType(cmd.getToBizType());
                forceCmd.setBizId(cmd.getToBizId());
                forceOccupyOnLines(lineIds, remaining, forceCmd);
            }
            OccupyResultVO vo = OccupyResultVO.ok(moved, lineIds);
            vo.setMessage("转移成功 " + moved);
            return vo;
        } finally {
            redisLockUtil.unlock("budget:transfer:" + cmd.getFromBizType() + ":" + cmd.getFromBizId(), token);
        }
    }

    // ---------------- 月度调整（R8：一律走 BUDGET 审批） ----------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean adjustAmount(Long budgetLineId, BigDecimal newAmount, String reason) {
        if (budgetLineId == null || newAmount == null || newAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "调整参数非法");
        }
        BudgetLine line = budgetLineMapper.selectForUpdateById(budgetLineId);
        if (line == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "预算行不存在：" + budgetLineId);
        }
        if (newAmount.compareTo(line.getUsedAmount() == null ? BigDecimal.ZERO : line.getUsedAmount()) < 0) {
            throw new BizException(ResultCode.BIZ_ERROR,
                    "调减后额度 " + newAmount + " 不能低于已占用 " + line.getUsedAmount());
        }
        // R8（PRD §6.4.3 L635：审批通过后更新台账）：取消 20% 免审阈值（Q4 作废）——
        // 调增/调减一律 create BUDGET 审批；通过后由 BudgetApprovalHandler 按 payload 生效
        // （前后值留痕：payload + ADJUST log）
        BigDecimal oldAmount = line.getAmount() == null ? BigDecimal.ZERO : line.getAmount();
        BigDecimal delta = newAmount.subtract(oldAmount);
        JSONObject payload = new JSONObject();
        payload.set("adjust", true);
        payload.set("lineId", budgetLineId);
        payload.set("newAmount", newAmount);
        payload.set("oldAmount", oldAmount);
        payload.set("delta", delta);
        payload.set("reason", reason);
        com.dzgylxt.approval.ApprovalTaskSpec spec = new com.dzgylxt.approval.ApprovalTaskSpec();
        spec.setBizType("BUDGET");
        spec.setBizId(budgetLineId);
        spec.setTitle("预算月度调整-行" + budgetLineId);
        spec.setApplicant(UserContext.getCurrentUsername());
        spec.setPayloadJson(payload.toString());
        com.dzgylxt.approval.ApprovalGateway gateway = gatewayProvider.getIfAvailable();
        if (gateway != null) {
            gateway.create(spec);
        }
        return true;
    }

    // ---------------- 对账辅助 ----------------

    @Override
    public BigDecimal bizOccupied(Long budgetLineId, BudgetBizType bizType, Long bizId) {
        return occupyLogMapper.sumBizOccupied(budgetLineId, bizType.getValue(), bizId);
    }

    @Override
    public BigDecimal occupiedTotal(BudgetBizType bizType, Long bizId) {
        BigDecimal total = BigDecimal.ZERO;
        for (Long lineId : occupyLogMapper.selectLineIdsByBiz(bizType.getValue(), bizId)) {
            total = total.add(occupyLogMapper.sumBizOccupied(lineId, bizType.getValue(), bizId));
        }
        return total;
    }

    @Override
    public void recordAdjustLog(Long budgetLineId, BudgetOccupyCmd cmd, BigDecimal delta,
                                BigDecimal balanceBefore, BigDecimal balanceAfter) {
        BudgetLine line = budgetLineMapper.selectById(budgetLineId);
        if (line == null) {
            return;
        }
        cmd.setPeriod(line.getPeriod());
        writeLog(line, cmd, BudgetAction.ADJUST, delta, balanceBefore, balanceAfter);
    }

    // ---------------- 内部工具 ----------------

    /** 锁定控制单元的全部月度行（subjectId 空 = 部门当月全部科目；按 id 升序逐行 FOR UPDATE）。 */
    private List<BudgetLine> lockMonthlyLines(Long deptId, int year, Long subjectId, int period) {
        List<BudgetLine> candidates = subjectId == null
                ? budgetLineMapper.selectDeptMonthlyLines(deptId, year, period)
                : filterByPeriod(budgetLineMapper.selectMonthlyLines(deptId, year, subjectId), period);
        List<BudgetLine> locked = new ArrayList<>();
        for (BudgetLine candidate : candidates) {
            BudgetLine row = budgetLineMapper.selectForUpdateById(candidate.getId());
            if (row != null) {
                locked.add(row);
            }
        }
        return locked;
    }

    private List<BudgetLine> filterByPeriod(List<BudgetLine> lines, int period) {
        List<BudgetLine> result = new ArrayList<>();
        for (BudgetLine line : lines) {
            if (line.getPeriod() != null && line.getPeriod() == period) {
                result.add(line);
            }
        }
        return result;
    }

    /**
     * 分摊写入占用：逐行 min(剩余需占, 行可用)；force 时末行兜底超支（允许 used &gt; amount）。
     */
    private OccupyResultVO distribute(BudgetOccupyCmd cmd, List<BudgetLine> locked,
                                      BigDecimal amount, boolean force) {
        BigDecimal remaining = amount;
        List<Long> touched = new ArrayList<>();
        for (int i = 0; i < locked.size(); i++) {
            BudgetLine line = locked.get(i);
            BigDecimal take;
            if (!force && i < locked.size() - 1) {
                take = availableOf(line).min(remaining);
            } else if (force) {
                // 超支：逐行吃满可用，末行兜底全部剩余（used_amount 允许 > amount）
                take = (i == locked.size() - 1) ? remaining : availableOf(line).min(remaining);
            } else {
                take = remaining; // 末行且已校验足额
            }
            if (take.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal[] bal = updateUsedWithRetry(line, take);
            writeLog(line, cmd, BudgetAction.OCCUPY, take, bal[0], bal[1]);
            touched.add(line.getId());
            remaining = remaining.subtract(take);
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
        }
        return OccupyResultVO.ok(amount.subtract(remaining.max(BigDecimal.ZERO)), touched);
    }

    /** force 场景的指定行超支占用（转移差额兜底）。 */
    private void forceOccupyOnLines(List<Long> lineIds, BigDecimal amount, BudgetOccupyCmd cmd) {
        BigDecimal remaining = amount;
        for (int i = 0; i < lineIds.size() && remaining.compareTo(BigDecimal.ZERO) > 0; i++) {
            BudgetLine line = budgetLineMapper.selectForUpdateById(lineIds.get(i));
            if (line == null) {
                continue;
            }
            BigDecimal take = (i == lineIds.size() - 1) ? remaining
                    : availableOf(line).max(BigDecimal.ZERO).min(remaining);
            if (take.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal[] snap = updateUsedWithRetry(line, take);
            writeLog(line, cmd, BudgetAction.OCCUPY, take, snap[0], snap[1]);
            remaining = remaining.subtract(take);
        }
    }

    /**
     * 条件更新 used_amount（version 兜底，冲突重试 1 次）；返回 [balanceBefore, balanceAfter]。
     *
     * <p>P2b-11：区分两类失败——重读后 version 未变 = 非并发冲突，而是余额守卫
     * （{@code used + delta < 0}，CHANGE SQL 守卫）→ 报 BIZ_ERROR 业务语义
     * （如"释放超过占用余额"）；仅 version 确有变化且重试仍失败才报 3002 并发冲突
     * （历史缺陷：混报 3002 掩盖了 P2b-9 真因）。</p>
     */
    private BigDecimal[] updateUsedWithRetry(BudgetLine line, BigDecimal delta) {
        BigDecimal before = line.getUsedAmount() == null ? BigDecimal.ZERO : line.getUsedAmount();
        int updated = budgetLineMapper.changeUsedAmount(line.getId(), delta, line.getVersion());
        if (updated == 0) {
            BudgetLine fresh = budgetLineMapper.selectById(line.getId());
            if (fresh == null) {
                throw new BizException(ResultCode.DATA_NOT_FOUND, "预算行不存在（行 " + line.getId() + "）");
            }
            if (fresh.getVersion() != null && fresh.getVersion().equals(line.getVersion())) {
                // 版本未变 = 无并发写入，失败源于余额守卫 → 业务语义错误（非 3002）
                throw new BizException(ResultCode.BIZ_ERROR,
                        delta.signum() < 0
                                ? "释放超过占用余额（行 " + line.getId() + "），请按实际占用余额操作"
                                : "预算行余额守卫失败（行 " + line.getId() + "）");
            }
            updated = budgetLineMapper.changeUsedAmount(line.getId(), delta, fresh.getVersion());
        }
        if (updated == 0) {
            throw new BizException(ResultCode.DATA_CONFLICT, "预算行并发冲突，请重试（行 " + line.getId() + "）");
        }
        BigDecimal after = before.add(delta);
        line.setUsedAmount(after);
        line.setVersion(line.getVersion() + 1);
        return new BigDecimal[]{before, after};
    }

    /** 写流水（P2b-9 方案 A：OCCUPY/RELEASE 带符号落账，WRITE_OFF/ADJUST 见各调用点；核销 balance 前后相等）。 */
    private void writeLog(BudgetLine line, BudgetOccupyCmd cmd, BudgetAction action,
                          BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter) {
        BudgetOccupyLog entry = new BudgetOccupyLog();
        entry.setBudgetLineId(line.getId());
        entry.setBizType(cmd.getBizType());
        entry.setBizId(cmd.getBizId());
        entry.setAction(action);
        entry.setAmount(amount);
        entry.setBalanceBefore(balanceBefore);
        entry.setBalanceAfter(balanceAfter);
        // 调整为人工动作落操作人；占用/释放/核销为系统动作（NULL）
        entry.setOperator(action == BudgetAction.ADJUST ? UserContext.getCurrentUserId() : null);
        entry.setRemark(cmd.getRemark());
        occupyLogMapper.insert(entry);
    }

    /** 占用月份：expectedDate 属当前年取其月，否则提交当月（Q1b）。 */
    private int resolvePeriod(BudgetOccupyCmd cmd) {
        if (cmd.getPeriod() != null && cmd.getPeriod() >= 1 && cmd.getPeriod() <= 12) {
            return cmd.getPeriod();
        }
        LocalDate now = LocalDate.now();
        if (cmd.getExpectedDate() != null && cmd.getExpectedDate().getYear() == now.getYear()) {
            return cmd.getExpectedDate().getMonthValue();
        }
        return now.getMonthValue();
    }

    private BigDecimal availableOf(BudgetLine line) {
        BigDecimal amount = line.getAmount() == null ? BigDecimal.ZERO : line.getAmount();
        BigDecimal used = line.getUsedAmount() == null ? BigDecimal.ZERO : line.getUsedAmount();
        return amount.subtract(used);
    }

    private BudgetOccupyCmd baseCmdOf(BudgetLine line) {
        BudgetOccupyCmd cmd = new BudgetOccupyCmd();
        if (line != null) {
            cmd.setPeriod(line.getPeriod());
        }
        return cmd;
    }

    private void validateCmd(BudgetOccupyCmd cmd) {
        if (cmd == null || cmd.getDeptId() == null || cmd.getAmount() == null
                || cmd.getBizType() == null || cmd.getBizId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "预算占用命令非法");
        }
    }

    private void validateBiz(BudgetOccupyCmd cmd) {
        if (cmd == null || cmd.getBizType() == null || cmd.getBizId() == null
                || cmd.getAmount() == null || cmd.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "预算动作命令非法");
        }
    }

    /** 构造 BUDGET 审批任务（调整场景直插，保持与网关 create 相同形态）。 */
    private ApprovalTask buildTask(com.dzgylxt.approval.ApprovalTaskSpec spec) {
        ApprovalTask task = new ApprovalTask();
        task.setBizType(spec.getBizType());
        task.setBizId(spec.getBizId());
        task.setFlowKey(spec.getBizType());
        task.setStatus(ApprovalStatus.CREATED);
        task.setCurrentNode("end");
        task.setRemark(spec.getTitle());
        return task;
    }
}
