package com.dzgylxt.entity.approval;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.ApprovalStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

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
    /** 审批负载 JSON（P3 §4：BUDGET 超支/调整、SETTLEMENT/PAYMENT 摘要；回调侧读取） */
    private String payloadJson;
    private String remark;
    /** 申请人用户名快照（P4 缺口④；展示用，通知投递用 applicantId） */
    private String applicant;
    /** 申请人用户 id（通知/重提归属，P4 缺口④） */
    private Long applicantId;
    /** 金额摘要（创建时从 payloadJson.amount 提取冗余，工作台展示+节点金额区间匹配） */
    private BigDecimal amount;
    /** 提交时流程版本快照（在途任务按提交时配置走完，拍板清单声明） */
    private Integer flowVersion;
    /** 乐观锁版本（并发防重审，P4 规格 §3.4；条件更新主控，不用 MP @Version 拦截器） */
    private Integer version;
}
