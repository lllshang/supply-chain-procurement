package com.dzgylxt.entity.order;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableField;
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
    /** 本单占用预算（P3 起为真实占用：下单时自申请转移，取消/核销经 IBudgetOccupyService） */
    private BigDecimal budgetOccupied;
    /** 阶段结算比例 JSON（如 [{"phase":1,"ratio":30},{"phase":2,"ratio":40}]；null=一次性，P3 §1.3.3） */
    private String phasePlan;
    private String remark;

    /** R5：派生展示字段——结清进度 = Σ有效结算金额 / 应结总额（0~1）；不参与订单状态机。 */
    @TableField(exist = false)
    private BigDecimal settleProgress;
    /** R5：派生展示字段——付清进度 = Σ已付付款金额 / 应结总额（0~1）；不参与订单状态机。 */
    @TableField(exist = false)
    private BigDecimal paidProgress;
}
