package com.dzgylxt.vo.approval;

import lombok.Data;

import com.dzgylxt.enums.ApprovalNodeStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批任务详情（工作台详情抽屉取数：任务头 + 节点链时间轴 + 审批记录）。
 */
@Data
public class ApprovalTaskDetailVO implements Serializable {

    private Long taskId;
    private String bizType;
    private Long bizId;
    private String title;
    private String applicant;
    private BigDecimal amount;
    /** 状态（ApprovalStatus 枚举名，字符串契约 #33） */
    private String status;
    private String currentNode;
    private LocalDateTime createdAt;

    /** 节点链时间轴（approval_node 快照行按 seq 升序）。 */
    private List<NodeItem> nodes;

    /** 审批记录（approval_record，按 id 升序）。 */
    private List<RecordItem> records;

    /** 当前用户是否可审批该任务（当前节点候选人）。 */
    private Boolean canApprove;

    @Data
    public static class NodeItem implements Serializable {
        private Long nodeId;
        private String nodeCode;
        private String nodeName;
        private Integer seq;
        private String signType;
        /** 节点状态：0=待审 1=已审结 2=已跳过（int 直传，前端 map 中文） */
        private ApprovalNodeStatus status;
        private Long approver;
        private String action;
        private String comment;
    }

    @Data
    public static class RecordItem implements Serializable {
        private Long nodeId;
        private Long approver;
        private String approverName;
        private String action;
        private String comment;
        private LocalDateTime createdAt;
    }
}
