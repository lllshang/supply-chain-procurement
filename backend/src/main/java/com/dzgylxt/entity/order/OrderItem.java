package com.dzgylxt.entity.order;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

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
    /** 申请明细追溯（与 source_award_item 二选一或并存，P2 §1.3.8） */
    private Long applyItemId;
    /** 到货计划日期（P2-T09 逾期扫描依据） */
    private LocalDate planDate;
    /** 计划数量（基本单位，可分批多计划行） */
    private BigDecimal plannedQty;
}
