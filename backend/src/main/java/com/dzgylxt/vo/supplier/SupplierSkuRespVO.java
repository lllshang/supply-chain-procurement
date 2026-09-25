package com.dzgylxt.vo.supplier;

import com.dzgylxt.enums.BindScope;
import com.dzgylxt.enums.SupplierSkuStatus;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 供应商-商品绑定响应。
 */
@Data
public class SupplierSkuRespVO implements Serializable {

    private Long id;
    private Long supplierId;
    private Long skuId;
    private String skuCode;
    private String supplierSkuCode;
    private BigDecimal supplyPrice;
    private String packageUnit;
    /** 绑定范围：0=不限定 1=限定报价接单 */
    private BindScope bindScope;
    /** 0=正常 1=停用 */
    private SupplierSkuStatus status;
}
