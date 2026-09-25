package com.dzgylxt.vo.catalog;

import lombok.Data;

import com.dzgylxt.enums.CatalogStatus;

import java.io.Serializable;

/**
 * 商品品类新增/编辑请求。
 */
@Data
public class ProductCategorySaveReqVO implements Serializable {

    /** 父节点ID，0=根 */
    private Long parentId;
    private String code;
    private String name;
    /** 0=有效 1=无效（新增默认 0） */
    private CatalogStatus status;
}
