package com.dzgylxt.vo.budget;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 预算台账明细响应（period 维度：0=年，1–12=月）。
 */
@Data
public class BudgetLineRespVO implements Serializable {

    private Long id;
    private Long headerId;
    private Long subjectId;
    private String subjectName;
    private Long projectId;
    private String projectName;
    /** 0=年度总额 1–12=月 */
    private Integer period;
    private BigDecimal amount;
    private BigDecimal usedAmount;
    private Integer version;
}
