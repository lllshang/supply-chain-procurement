package com.dzgylxt.vo.catalog;

import lombok.Data;

import com.dzgylxt.enums.CatalogStatus;

import java.io.Serializable;

/**
 * 产品导出筛选请求。
 */
@Data
public class ProductExportReqVO implements Serializable {

    private Long categoryId;
    private CatalogStatus status;
    private String keyword;
    /** 导出列集（逗号分隔，可配置；空=默认列） */
    private String columns;
}
