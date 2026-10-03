package com.dzgylxt.vo.catalog;

import com.dzgylxt.enums.ProductStatus;
import com.dzgylxt.enums.ValuationType;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

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

    // ===== 编辑对话框全量对齐原型 =====
    /** 单位换算系数：1 采购单位 = N 基本单位（多规格模式逐行覆盖） */
    private Integer unitConversionFactor;
    /** 多规格取值（multiple 模式）：[{attributeId, value}] */
    private List<SpecValueDTO> specValues;

    /** 多规格取值。 */
    @Data
    public static class SpecValueDTO implements Serializable {
        private String attributeId;
        private String value;
    }
}
