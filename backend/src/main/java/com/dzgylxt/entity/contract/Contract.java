package com.dzgylxt.entity.contract;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.ContractStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合同（仅 EFFECTIVE 可发起订单）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("contract")
public class Contract extends BaseEntity implements Serializable {

    private Long supplierId;
    private String no;
    private String title;
    private BigDecimal amount;
    private LocalDate validFrom;
    private LocalDate validTo;
    private ContractStatus status;
    private BigDecimal availableAmount;
    private String remark;
}
