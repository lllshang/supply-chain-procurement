package com.dzgylxt.vo.purchase;

import com.dzgylxt.enums.ItemType;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/** 常购清单带入生成的申请明细草稿（设计 §2.1 bringIn）。 */
@Data
public class ApplyItemDraftVO implements Serializable {

    private Long skuId;
    private Long frequentId;
    private String skuCode;
    private String skuName;
    /** 数量（采购单位，默认取常购 default_qty） */
    private BigDecimal qty;
    private String purchaseUnit;
    /** 最近价（最近订单/报价价，无则 standard_price） */
    private BigDecimal priceEstimate;
    private ItemType itemType;
    private String remark;
}
