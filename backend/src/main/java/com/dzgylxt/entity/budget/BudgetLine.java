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
 * <p>维度（R1 修订，PRD §6.4.1 L609 / 数据模型 L1197）：控制单元 = <b>部门（budget_header.deptId）
 * × 科目(subjectId) × 月份(period 1–12)</b>；月度可用 = amount − used_amount。</p>
 * <ul>
 *   <li><b>period=0 年度额度行已拆除（R1）</b>：年度 = 12 个月度行的聚合视图，
 *       不落库为独立额度行（历史存量行由 p3_r1_migration.sql 清理）；</li>
 *   <li><b>projectId 退出额度控制（R1）</b>：仅保留为业务归属/统计冗余字段，
 *       不参与校验/占用/释放/核销的额度维度。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("budget_line")
public class BudgetLine extends BaseEntity implements Serializable {

    private Long headerId;
    private Long subjectId;
    /** 预算项目ID（R1：统计冗余字段，不参与额度控制；NULL=无项目归属） */
    private Long projectId;
    /** 月份 1-12（R1：period=0 年度额度行已拆除，年度=12 个月度行聚合视图） */
    private Integer period;
    private BigDecimal amount;
    private BigDecimal usedAmount;
    private Integer version;
}
