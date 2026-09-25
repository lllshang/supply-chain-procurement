package com.dzgylxt.vo.supplier;

import lombok.Data;

import com.dzgylxt.enums.CatalogStatus;

import java.io.Serializable;

/**
 * 供应商分类新增/编辑请求。
 */
@Data
public class SupplierCategorySaveReqVO implements Serializable {

    /** 父节点ID，0=根 */
    private Long parentId;
    private String code;
    private String name;
    /** 0=有效 1=无效 */
    private CatalogStatus status;
}
