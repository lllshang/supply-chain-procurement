package com.dzgylxt.vo.catalog;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 产品导入行模型（单规格 / 多规格共用）。
 *
 * <p>多规格行以 {@code specCombos} 列承载"规格名:值;规格名:值"的组合串，
 * 导入时按组合展开为多条 SKU。</p>
 */
@Data
public class ProductImportRowVO implements Serializable {

    @ExcelProperty("SPU编码")
    private String spuCode;
    @ExcelProperty("SPU名称")
    private String spuName;
    @ExcelProperty("品类编码")
    private String categoryCode;
    @ExcelProperty("基本单位")
    private String baseUnit;
    @ExcelProperty("采购单位")
    private String purchaseUnit;
    @ExcelProperty("SKU编码")
    private String skuCode;
    @ExcelProperty("条码")
    private String barcode;
    @ExcelProperty("规格")
    private String spec;
    /** 多规格组合，如 "颜色:红;尺寸:S"；单规格留空 */
    @ExcelProperty("规格组合")
    private String specCombos;
    @ExcelProperty("参考价")
    private BigDecimal referencePrice;
    @ExcelProperty("标准价")
    private BigDecimal standardPrice;
    /** 计价方式：0=计件 1=计重（缺省 0） */
    @ExcelProperty("计价方式")
    private Integer valuationType;
    @ExcelProperty("商品简介")
    private String description;
}
