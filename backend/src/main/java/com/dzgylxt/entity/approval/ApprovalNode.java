package com.dzgylxt.entity.approval;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 审批节点（会签 / 或签 / 逐级）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("approval_node")
public class ApprovalNode extends BaseEntity implements Serializable {

    private Long taskId;
    private String nodeDef;
    private Long approver;
    private String action;
    private String comment;
    private Integer status;
}
