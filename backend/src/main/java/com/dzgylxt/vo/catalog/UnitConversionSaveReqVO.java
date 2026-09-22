package com.dzgylxt.vo.catalog;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单位换算保存请求（rate/version/effectiveFrom）。
 */
@Data
public class UnitConversionSaveReqVO implements Serializable {

    private Long skuId;
    /** 采购单位（引用 unit.code） */
    private String fromUnit;
    /** 基本单位（引用 unit.code） */
    private String toUnit;
    /** 换算率（>0） */
    private BigDecimal rate;
    private LocalDateTime effectiveFrom;
}
