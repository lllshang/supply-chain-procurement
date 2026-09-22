package com.dzgylxt.entity.order;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.AdjustBizType;
import com.dzgylxt.enums.AdjustStatus;
import com.dzgylxt.enums.AdjustType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 履约调整台账（设计 §1.2.7；变更留痕，金额超合同 5% 走 FULFILLMENT_ADJUST 审批 <!-- D5 -->）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("fulfillment_adjust")
public class FulfillmentAdjust extends BaseEntity implements Serializable {

    /** 调整单号 LY-{yyyy}{MM}-{seq6}（全局唯一） */
    private String adjustNo;
    /** 0=订单 1=到货 */
    private AdjustBizType bizType;
    /** 订单ID */
    private Long orderId;
    /** 到货单ID（可空） */
    private Long arrivalId;
    /** 0=差异 1=退货 2=补货 3=变更 */
    private AdjustType adjustType;
    /** 调整前口径快照（金额/数量） */
    private String beforeJson;
    /** 调整后口径快照 */
    private String afterJson;
    private String reason;
    /** 0=草稿 1=审批中 2=生效 3=驳回 */
    private AdjustStatus status;
    /** 附件 JSON 数组 */
    private String fileKeys;
    private Long operator;
}
