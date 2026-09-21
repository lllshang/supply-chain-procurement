package com.dzgylxt.entity;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 预算明细行（行锁对象：used = 占用 + 核销）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("budget_line")
public class BudgetLine extends BaseEntity implements Serializable {

    private Long headerId;
    private Long subjectId;
    /** 月份 1-12（0 表示年度总额） */
    private Integer period;
    private BigDecimal amount;
    private BigDecimal usedAmount;
    private Integer version;
}
