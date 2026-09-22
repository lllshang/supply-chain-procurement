package com.dzgylxt.entity.approval;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.ApprovalStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 审批单据（审批中心表驱动）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("approval_task")
public class ApprovalTask extends BaseEntity implements Serializable {

    private String bizType;
    private Long bizId;
    private String flowKey;
    private ApprovalStatus status;
    private String currentNode;
    private String remark;
}
