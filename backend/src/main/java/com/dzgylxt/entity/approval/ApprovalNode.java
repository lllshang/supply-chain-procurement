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
    /** 节点编码快照（P4 §2.9：=approval_node_def.node_code，节点链结构冻结） */
    private String nodeCode;
    /** 节点序快照 */
    private Integer seq;
    /** 签类型快照：ANY/ALL */
    private String signType;
    /** 乐观锁版本（节点行并发防护，P4 规格 §3.4） */
    private Integer version;
}
