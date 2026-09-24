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
    /**
     * 预算科目ID（QA2-01：额度控制键=部门×月份×科目（PRD §6.4.1 L609 / PR-01），
     * 占用/再校验/转移均按此科目落预算行；NULL=待补录（存量数据），提交前必须填写。
     */
    private Long budgetSubjectId;
    /** 采购用途（PR-01，P2b/S9） */
    private String purpose;
    /** 手工项目名（PR-01：无预算项目时的业务归属，P2b/S9） */
    private String projectName;
    private Long applicantId;
    /** 期望到货日期（P2 §1.3.1） */
    private LocalDate expectedDate;
    private String remark;
}
