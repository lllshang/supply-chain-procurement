package com.dzgylxt.entity.catalog;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

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
    private Integer status;
}
