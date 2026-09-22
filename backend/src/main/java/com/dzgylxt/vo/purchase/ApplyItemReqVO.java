package com.dzgylxt.vo.purchase;

import com.dzgylxt.enums.ItemType;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/** 采购申请明细行请求（设计 §2.1）。 */
@Data
public class ApplyItemReqVO implements Serializable {

    private Long skuId;
    /** 数量（采购单位） */
    private BigDecimal qty;
    /** 采购单位（unit.code；缺省取 sku.purchase_unit，再缺省 base_unit） */
    private String purchaseUnit;
    /** 预估单价 */
    private BigDecimal priceEstimate;
    /** 行级类型：0=物料 1=服务（缺省物料） */
    private ItemType itemType;
    private String remark;
}
