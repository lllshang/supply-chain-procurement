package com.dzgylxt.entity.order;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.ItemType;
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
    /** 0=物料 1=服务（源自申请 item_type 拆单，P2 §1.3.7） */
    private ItemType orderType;
    private OrderStatus status;
    /** 本单金额快照（<!-- D3: P3 改为真实占用 -->） */
    private BigDecimal budgetOccupied;
    private String remark;
}
