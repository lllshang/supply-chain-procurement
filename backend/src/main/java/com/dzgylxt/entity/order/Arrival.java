package com.dzgylxt.entity.order;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.ArrivalStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 到货验收（部分入库 / 差异）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("arrival")
public class Arrival extends BaseEntity implements Serializable {

    private Long orderId;
    /** 到货单号 DH-{order_no}-{seq2}（全局唯一，P2 §1.3.9） */
    private String arrivalNo;
    private BigDecimal actualQty;
    private BigDecimal diffQty;
    private String voucherFileKey;
    private ArrivalStatus status;
    private String remark;
}
