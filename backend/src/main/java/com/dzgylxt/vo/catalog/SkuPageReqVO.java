package com.dzgylxt.vo.catalog;

import com.dzgylxt.enums.ProductStatus;
import lombok.Data;

import java.io.Serializable;

/**
 * SKU 分页查询请求。
 */
@Data
public class SkuPageReqVO implements Serializable {

    private Long spuId;
    private ProductStatus status;
    /** 关键字（匹配 skuCode / barcode） */
    private String keyword;
    private Long current = 1L;
    private Long size = 10L;
}
