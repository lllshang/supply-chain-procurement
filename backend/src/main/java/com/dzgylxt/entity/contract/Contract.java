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
 * 合同（仅 EFFECTIVE 且在有效期可发起订单）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("contract")
public class Contract extends BaseEntity implements Serializable {

    private Long supplierId;
    /** 来源定标（<!-- D2: P2b 线下补录可空 -->，P2 §1.3.6） */
    private Long awardId;
    private String no;
    private String title;
    /** 0=物料 1=服务 2=综合（P2 新增） */
    private Integer contractType;
    private BigDecimal amount;
    private LocalDate validFrom;
    private LocalDate validTo;
    /** 附件 JSON 数组（file_meta.file_key，多附件，P2 新增） */
    private String fileKeys;
    /** 续签来源合同（<!-- D1: P2b 框架续签 -->，P2 新增） */
    private Long renewedFromId;
    /** 终止原因（P2 新增） */
    private String terminateReason;
    private ContractStatus status;
    private BigDecimal availableAmount;
    private String remark;
    /** 乐观锁版本（额度扣减并发兜底，主防=行锁，P2 新增） */
    private Integer version;
}
