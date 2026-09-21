package com.dzgylxt.entity;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.PaymentStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 付款登记（线下付款，状态回写）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("payment")
public class Payment extends BaseEntity implements Serializable {

    private Long settlementId;
    private BigDecimal payAmount;
    private String payMethod;
    private String voucherFile;
    private PaymentStatus status;
    private String remark;
}
