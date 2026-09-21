package com.dzgylxt.entity;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.SettlementStatus;
import com.dzgylxt.enums.SettlementType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 结算（从订单或到货单发起）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("settlement")
public class Settlement extends BaseEntity implements Serializable {

    private Long orderId;
    private Long arrivalId;
    private SettlementType type;
    private BigDecimal amount;
    private SettlementStatus status;
    private String remark;
}
