package com.dzgylxt.entity.purchase;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.PurchaseApplyStatus;
import com.dzgylxt.enums.PurchaseApplyType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 采购申请。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("purchase_apply")
public class PurchaseApply extends BaseEntity implements Serializable {

    private Long deptId;
    private String applyNo;
    private String title;
    private PurchaseApplyType type;
    private PurchaseApplyStatus status;
    /** 预算状态：0=未校验，1=通过，2=超预算 */
    private Integer budgetStatus;
    private Long applicantId;
    /** 期望到货日期（P2 §1.3.1） */
    private LocalDate expectedDate;
    private String remark;
}
