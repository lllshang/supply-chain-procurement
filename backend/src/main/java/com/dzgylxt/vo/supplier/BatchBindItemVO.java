package com.dzgylxt.vo.supplier;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 批量绑定 Excel 行模型。
 */
@Data
public class BatchBindItemVO implements Serializable {

    @ExcelProperty("供应商ID")
    private Long supplierId;
    @ExcelProperty("SKU ID")
    private Long skuId;
    @ExcelProperty("供应商货号")
    private String supplierSkuCode;
    @ExcelProperty("供货价")
    private BigDecimal supplyPrice;
    @ExcelProperty("包装单位")
    private String packageUnit;
    /** 绑定范围：0=不限定 1=限定报价接单 */
    @ExcelProperty("绑定范围")
    private Integer bindScope;
}
