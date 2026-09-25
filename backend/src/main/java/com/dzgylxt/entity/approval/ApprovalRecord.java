package com.dzgylxt.entity.approval;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 审批记录。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("approval_record")
public class ApprovalRecord extends BaseEntity implements Serializable {

    private Long taskId;
    private Long nodeId;
    private Long approver;
    private String action;
    private String comment;
    /** 审批人姓名快照（P4 规格 §3.5：留痕防用户改名） */
    private String approverName;
}
