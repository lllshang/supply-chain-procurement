package com.dzgylxt.entity.purchase;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 定标明细（设计 §1.2.1；按 SKU 可拆分多供应商，order_item.source_award_item 引用）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("award_item")
public class AwardItem extends BaseEntity implements Serializable {

    /** 定标单ID（逻辑外键 award.id） */
    private Long awardId;
    /** SKU（逻辑外键 sku.id） */
    private Long skuId;
    /** 中标供应商（按 SKU 可拆分多家） */
    private Long supplierId;
    /** 定标单价（基本单位口径） */
    private BigDecimal price;
    /** 定标数量（采购单位） */
    private BigDecimal qty;
    /** 基本单位数量 = qty × conv_rate_snapshot */
    private BigDecimal qtyInBaseUnit;
    /** 换算快照（取 unit_conversion 当前生效版本） */
    private BigDecimal convRateSnapshot;
    private String remark;
}
