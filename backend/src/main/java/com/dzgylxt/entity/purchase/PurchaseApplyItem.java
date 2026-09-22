package com.dzgylxt.entity.purchase;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 采购申请明细（申请↔订单追溯）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("purchase_apply_item")
public class PurchaseApplyItem extends BaseEntity implements Serializable {

    private Long applyId;
    private Long skuId;
    private BigDecimal qty;
    private BigDecimal applyQty;
    private BigDecimal orderedQty;
    private BigDecimal remainQty;
    private String unitSnapshot;
}
