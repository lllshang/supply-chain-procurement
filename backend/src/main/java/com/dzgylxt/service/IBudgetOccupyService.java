package com.dzgylxt.service;

import com.dzgylxt.vo.budget.BudgetOccupyCmd;
import com.dzgylxt.vo.budget.BudgetTransferCmd;
import com.dzgylxt.vo.budget.OccupyResultVO;

import java.math.BigDecimal;

/**
 * 预算占用/释放/核销/转移/调整唯一写入口（P3 设计 §2.3 <!-- D3: 已落地 P3 -->）。
 *
 * <p>合规约束：</p>
 * <ul>
 *   <li><b>唯一入口</b>：禁止任何其他 Service 直改 {@code budget_line.used_amount}，
 *       全部动作必须经本服务（并发穿透防线，架构 2.2）；</li>
 *   <li><b>三重保障</b>：{@code budget_line} 行锁 FOR UPDATE（主）+ Redis 锁
 *       {@code budget:lock:{deptId}:{subjectId}:{period}}（多实例，细到月不同月不互阻）
 *       + {@code version} 乐观兜底（冲突重试 1 次）；</li>
 *   <li><b>金额守恒</b>：每笔动作写 {@code budget_occupy_log}（balance 前后快照），
 *       {@code Σlog(占用−释放) == used_amount} 为硬不变式，每日对账任务校验；</li>
 *   <li><b>超支</b>：仅 {@code BUDGET} 审批通过后执行（force=true，先占后审禁止）。</li>
 * </ul>
 */
public interface IBudgetOccupyService {

    /** 校验+占用+写 log（同事务；多行按 budget_line.id 升序加锁；余额不足返回 available=false 且零写入）。 */
    OccupyResultVO occupy(BudgetOccupyCmd cmd);

    /** 释放（≤该 biz 累计占用余额；不足按余额释放并告警）。 */
    OccupyResultVO release(BudgetOccupyCmd cmd);

    /** 核销：占用→核销（结算驱动），used_amount 不变，log balance 前后相等。 */
    OccupyResultVO writeOff(BudgetOccupyCmd cmd);

    /** 占用主体转移（申请→订单，amount 不变；源余额不足差额同行强制占用）。 */
    OccupyResultVO transfer(BudgetTransferCmd cmd);

    /**
     * 月度调整：调增直生效；调减校验 {@code newAmount ≥ used_amount}；
     * 变幅超阈值（{@code app.budget.adjust-threshold}，默认 20%）走 BUDGET 审批，
     * 返回 {@code pendingApproval=true} 且不落库（审批通过后由回调生效）。
     *
     * @return pendingApproval=true 表示已发 BUDGET 升级审批、本次未生效
     */
    boolean adjustAmount(Long budgetLineId, BigDecimal newAmount, String reason);

    /** 某业务单据在某行上的占用余额（Σ占用 − Σ释放 − Σ核销）。 */
    java.math.BigDecimal bizOccupied(Long budgetLineId, com.dzgylxt.enums.BudgetBizType bizType, Long bizId);

    /** 某业务单据的占用余额合计（跨行；日志为唯一依据，供按余额整体释放/展示）。 */
    java.math.BigDecimal occupiedTotal(com.dzgylxt.enums.BudgetBizType bizType, Long bizId);

    /** 补写月度调整（ADJUST）流水——仅 BUDGET 审批回调（超阈值调整生效时）调用，used_amount 不变。 */
    void recordAdjustLog(Long budgetLineId, BudgetOccupyCmd cmd, BigDecimal delta,
                         BigDecimal balanceBefore, BigDecimal balanceAfter);
}
