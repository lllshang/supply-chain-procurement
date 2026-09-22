package com.dzgylxt.entity.order;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.OrderStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 采购订单（基于有效合同发起）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("purchase_order")
public class PurchaseOrder extends BaseEntity implements Serializable {

    private Long contractId;
    private Long applyId;
    private Long supplierId;
    private String orderNo;
    private OrderStatus status;
    private BigDecimal budgetOccupied;
    private String remark;
}
