package com.dzgylxt.entity.order;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.ChangeStatus;
import com.dzgylxt.enums.ChangeType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 订单变更留痕（设计 §1.2.4；Q5：免审直接生效，重跑三重校验）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("order_change")
public class OrderChange extends BaseEntity implements Serializable {

    /** 订单ID */
    private Long orderId;
    /** 1=数量 2=价格 3=明细 4=取消 */
    private ChangeType changeType;
    /** 变更前快照（明细/金额/数量） */
    private String beforeJson;
    /** 变更后快照 */
    private String afterJson;
    private String reason;
    /** 0=待审 1=生效 2=驳回 */
    private ChangeStatus status;
    /** 操作人 */
    private Long operator;
}
