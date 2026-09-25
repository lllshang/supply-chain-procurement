package com.dzgylxt.vo.budget;

import lombok.Data;

import com.dzgylxt.enums.CatalogStatus;

import java.io.Serializable;

/**
 * 预算科目新增/编辑请求。
 */
@Data
public class BudgetSubjectSaveReqVO implements Serializable {

    private String code;
    private String name;
    /** 父科目ID，0=根 */
    private Long parentId;
    /** 科目类型：1=支出 2=收入 */
    private Integer subjectType;
    /** 0=有效 1=无效 */
    private CatalogStatus status;
}
