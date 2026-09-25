package com.dzgylxt.vo.budget;

import lombok.Data;

import com.dzgylxt.enums.CatalogStatus;

import java.io.Serializable;

/**
 * 预算项目新增/编辑请求。
 */
@Data
public class BudgetProjectSaveReqVO implements Serializable {

    private String code;
    private String name;
    private Integer year;
    /** 0=有效 1=无效 */
    private CatalogStatus status;
}
