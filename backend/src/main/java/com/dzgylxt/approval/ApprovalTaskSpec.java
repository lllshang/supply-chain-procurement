package com.dzgylxt.approval;

import lombok.Data;

import java.io.Serializable;

/**
 * 审批发起规格（跨域通用）。业务侧（采购申请/合同/定标/预算升级）构造后交由 {@link ApprovalGateway} 发起。
 */
@Data
public class ApprovalTaskSpec implements Serializable {
    /** 业务类型：PURCHASE / CONTRACT / AWARD / BUDGET_UPGRADE 等 */
    private String bizType;
    /** 业务单据 id */
    private Long bizId;
    /** 审批标题 */
    private String title;
    /** 发起人 */
    private String applicant;
    /** 业务载荷（JSON），供审批中心/回调使用 */
    private String payloadJson;
}
