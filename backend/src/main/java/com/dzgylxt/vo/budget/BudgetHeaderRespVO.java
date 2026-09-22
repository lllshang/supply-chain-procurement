package com.dzgylxt.vo.budget;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预算头响应。
 */
@Data
public class BudgetHeaderRespVO implements Serializable {

    private Long id;
    private Integer year;
    private Long deptId;
    private BigDecimal totalAmount;
    /** 0=草稿 1=生效 2=归档 */
    private Integer status;
    private String remark;
    private LocalDateTime updatedAt;
}
