package com.dzgylxt.entity.catalog;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.BindScope;
import com.dzgylxt.enums.SupplierSkuStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 供应商-商品绑定（限定报价 / 接单范围，P1）。
 *
 * <p>同一 {@code supplier_id + sku_id} 仅一条有效绑定；{@code price_range}（String）为
 * 遗留兼容字段，以 {@link #supplyPrice}（DECIMAL(18,2)）为供货价权威字段。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("supplier_sku")
public class SupplierSku extends BaseEntity implements Serializable {

    private Long supplierId;
    private Long skuId;
    /** 供应商货号 */
    private String supplierSkuCode;
    /** 遗留价格区间（保留兼容） */
    private String priceRange;
    /** 供货价（≥0，以本字段为准） */
    private BigDecimal supplyPrice;
    /** 包装单位（引用 unit.code） */
    private String packageUnit;
    /** 绑定范围：0=不限定 1=限定报价接单 */
    private BindScope bindScope;
    /** 0=正常，1=停用 */
    private SupplierSkuStatus status;
}
