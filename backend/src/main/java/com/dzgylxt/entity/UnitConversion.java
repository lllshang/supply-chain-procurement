package com.dzgylxt.entity;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单位换算（采购单位↔基本单位，含版本与生效时间）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("unit_conversion")
public class UnitConversion extends BaseEntity implements Serializable {

    private Long skuId;
    private String fromUnit;
    private String toUnit;
    private BigDecimal rate;
    private LocalDateTime effectiveFrom;
    private Integer version;
}
