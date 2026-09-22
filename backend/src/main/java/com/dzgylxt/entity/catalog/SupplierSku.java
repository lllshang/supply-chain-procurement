package com.dzgylxt.entity.catalog;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 供应商-商品绑定（限定报价 / 接单范围，P1）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("supplier_sku")
public class SupplierSku extends BaseEntity implements Serializable {

    private Long supplierId;
    private Long skuId;
    private String priceRange;
    /** 0=正常，1=停用 */
    private Integer status;
}
