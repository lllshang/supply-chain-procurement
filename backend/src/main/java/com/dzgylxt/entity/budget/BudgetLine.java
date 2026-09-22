package com.dzgylxt.entity.budget;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 预算明细行（行锁对象：used = 占用 + 核销）。
 *
 * <p>维度：科目(subjectId) × 期间(period，0=年/1–12=月) × 可选项目(projectId, nullable)；
 * 部门维度锚定 {@code budget_header.deptId}。{@code usedAmount} 本阶段恒为 0，
 * 占用/释放/核销由 P3 实现。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("budget_line")
public class BudgetLine extends BaseEntity implements Serializable {

    private Long headerId;
    private Long subjectId;
    /** 预算项目ID（可选，引用 budget_project；NULL=未启用项目维度） */
    private Long projectId;
    /** 月份 1-12（0 表示年度总额） */
    private Integer period;
    private BigDecimal amount;
    private BigDecimal usedAmount;
    private Integer version;
}
