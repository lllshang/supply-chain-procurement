package com.dzgylxt.vo.supplier;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 供应商-商品绑定请求。
 */
@Data
public class SupplierSkuSaveReqVO implements Serializable {

    private Long supplierId;
    private Long skuId;
    /** 供应商货号 */
    private String supplierSkuCode;
    /** 供货价（≥0） */
    private BigDecimal supplyPrice;
    /** 包装单位（引用 unit.code） */
    private String packageUnit;
    /** 绑定范围：0=不限定 1=限定报价接单 */
    private Integer bindScope;
}
