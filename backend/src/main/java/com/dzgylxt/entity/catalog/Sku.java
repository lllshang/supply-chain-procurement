package com.dzgylxt.entity.catalog;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.ValuationType;
import com.dzgylxt.enums.ProductStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品 SKU（最小可交易单元）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sku")
public class Sku extends BaseEntity implements Serializable {

    private Long spuId;
    private String skuCode;
    private String barcode;
    private String baseUnit;
    private String spec;
    /** 0=正常，1=停用 */
    private ProductStatus status;
    /** 采购单位（引用 unit.code） */
    private String purchaseUnit;
    /** 参考价（≥0） */
    private BigDecimal referencePrice;
    /** 标准价（≥0） */
    private BigDecimal standardPrice;
    /** 计价方式：0=计件 1=计重 */
    private ValuationType valuationType;
    /** 主图 file_key（引用 file_meta.file_key） */
    private String imageFileKey;
}
