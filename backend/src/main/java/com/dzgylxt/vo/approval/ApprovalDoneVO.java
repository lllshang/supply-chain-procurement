package com.dzgylxt.vo.approval;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 已办审批列表行（approval_record 取数 + 任务头冗余，P4 设计 §4.2）。
 */
@Data
public class ApprovalDoneVO implements Serializable {

    /** 审批记录 id */
    private Long recordId;
    private Long taskId;
    private String bizType;
    private Long bizId;
    /** 任务标题（task.remark） */
    private String title;
    /** 结论：APPROVE / REJECT */
    private String action;
    private String comment;
    private String approverName;
    /** 审批时间（record.createdAt） */
    private LocalDateTime approvedAt;
}
