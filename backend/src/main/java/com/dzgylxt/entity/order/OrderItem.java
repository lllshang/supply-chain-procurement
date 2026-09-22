package com.dzgylxt.entity.order;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单明细（来源合同 / 定标追溯）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("order_item")
public class OrderItem extends BaseEntity implements Serializable {

    private Long orderId;
    private Long skuId;
    private BigDecimal qtyPurchase;
    private BigDecimal qtyBase;
    private String convSnapshot;
    private BigDecimal price;
    private Long sourceAwardItem;
}
