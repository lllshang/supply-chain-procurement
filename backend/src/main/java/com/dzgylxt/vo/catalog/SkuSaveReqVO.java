package com.dzgylxt.vo.catalog;

import com.dzgylxt.enums.ProductStatus;
import com.dzgylxt.enums.ValuationType;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * SKU 新增/编辑请求。
 */
@Data
public class SkuSaveReqVO implements Serializable {

    private Long spuId;
    private String skuCode;
    private String barcode;
    /** 基本单位（引用 unit.code） */
    private String baseUnit;
    private String spec;
    /** 采购单位（引用 unit.code） */
    private String purchaseUnit;
    private BigDecimal referencePrice;
    private BigDecimal standardPrice;
    /** 计价方式：0=计件 1=计重 */
    private ValuationType valuationType;
    private String imageFileKey;
    /** 0=正常 1=停用 */
    private ProductStatus status;
}
