package com.dzgylxt.entity.purchase;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.AwardStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 定标（驱动合同，不直接转单）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("award")
public class Award extends BaseEntity implements Serializable {

    private Long inquiryId;
    private Long supplierId;
    private BigDecimal amount;
    private AwardStatus status;
    private String remark;
}
