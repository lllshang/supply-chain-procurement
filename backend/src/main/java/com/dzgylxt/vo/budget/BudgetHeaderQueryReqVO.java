package com.dzgylxt.vo.budget;

import lombok.Data;

import java.io.Serializable;

/**
 * 预算头查询请求（部门数据权限由拦截器注入）。
 */
@Data
public class BudgetHeaderQueryReqVO implements Serializable {

    private Integer year;
    private Long deptId;
    private Long current = 1L;
    private Long size = 10L;
}
