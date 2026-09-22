package com.dzgylxt.vo.catalog;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SKU 分页响应。
 */
@Data
public class SkuPageRespVO implements Serializable {

    private Long id;
    private Long spuId;
    private String skuCode;
    private String barcode;
    private String spec;
    private String baseUnit;
    private String purchaseUnit;
    private BigDecimal referencePrice;
    private BigDecimal standardPrice;
    private Integer valuationType;
    /** 0=正常 1=停用 */
    private Integer status;
    private LocalDateTime updatedAt;
}
