package com.dzgylxt.entity.budget;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.BudgetHeaderStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 预算头（年度）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("budget_header")
public class BudgetHeader extends BaseEntity implements Serializable {

    private Integer year;
    private Long deptId;
    private BigDecimal totalAmount;
    private BudgetHeaderStatus status;
    private String remark;
}
