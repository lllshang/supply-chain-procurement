package com.dzgylxt.entity.budget;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.common.BaseEntity;
import com.dzgylxt.enums.BudgetAction;
import com.dzgylxt.enums.BudgetBizType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 预算占用/释放/核销流水（P3 设计 §1.2.1；每日对账唯一依据）。
 *
 * <p>硬不变式（架构 2.2-R2）：按 {@code budget_line_id} 满足
 * {@code Σ(amount where action=OCCUPY) − Σ(amount where action=RELEASE) == budget_line.used_amount}；
 * {@code WRITE_OFF}（核销）仅做构成转移，不改变 used_amount（balance 前后相等）。
 * {@code ADJUST} 为月度调整的 amount 变更留痕（不参与该恒等式）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("budget_occupy_log")
public class BudgetOccupyLog extends BaseEntity implements Serializable {
    /** 预算明细行ID（控制单元：部门×科目×月） */
    private Long budgetLineId;
    /** 业务类型：APPLY/AWARD/ORDER/SETTLEMENT/ADJUST */
    private BudgetBizType bizType;
    /** 业务单据ID */
    private Long bizId;
    /** 动作：占用/释放/核销/调整 */
    private BudgetAction action;
    /** 本笔发生额（占用/调增为正，释放/调减为负；核销为正且 used_amount 不变） */
    private BigDecimal amount;
    /** 动作前 used_amount 快照 */
    private BigDecimal balanceBefore;
    /** 动作后 used_amount 快照（核销时前后相等） */
    private BigDecimal balanceAfter;
    /** 操作人（系统动作为 NULL） */
    private Long operator;
    private String remark;
}
