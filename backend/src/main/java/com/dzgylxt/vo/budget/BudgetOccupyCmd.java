package com.dzgylxt.vo.budget;

import com.dzgylxt.enums.BudgetBizType;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 预算占用/释放/核销命令（P3 设计 §2.3）。
 *
 * <p>占用月份锚定 {@code expectedDate} 所在月（属当前年）否则提交当月（Q1b：
 * 硬控粒度 = 部门×科目×月份）。{@code subjectId} 为空时按部门当月全部科目行
 * 分摊占用（申请单无科目维度，log 逐行留痕）。</p>
 */
@Data
public class BudgetOccupyCmd implements Serializable {
    private Long deptId;
    /** 预算年度（null 取当前年） */
    private Integer year;
    /** 预算科目（null=部门当月全部科目分摊） */
    private Long subjectId;
    /** 预算月份 1-12（null=按 expectedDate/提交当月推导） */
    private Integer period;
    /** 发生金额（释放/核销时为待释放/待核销额度） */
    private BigDecimal amount;
    /** 业务类型（APPLY/AWARD/ORDER/SETTLEMENT/ADJUST） */
    private BudgetBizType bizType;
    /** 业务单据ID */
    private Long bizId;
    /** 期望到货日期（占用月份锚定；可空） */
    private LocalDate expectedDate;
    /** 超支占用（仅 BUDGET 审批通过后由 BudgetApprovalHandler 传入；先占后审禁止） */
    private boolean force;
    private String remark;
}
