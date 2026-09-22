package com.dzgylxt.approval;

/**
 * 审批回调业务处理器（设计 §3）。
 *
 * <p>每个支持审批的业务域（PURCHASE_APPLY / AWARD / CONTRACT / FULFILLMENT_ADJUST）
 * 实现本接口并在 {@code LocalApprovalGateway.callback} 中按 {@link #bizType()} 分发。
 * 网关负责幂等（任务状态一次性流转）；处理器负责把结论映射到业务状态机，
 * 并与网关回调同事务执行。</p>
 */
public interface ApprovalCallbackHandler {

    /** 处理器对应的业务类型（ApprovalTaskSpec.bizType / approval_task.biz_type）。 */
    String bizType();

    /** 审批通过回调（同事务）。 */
    void onApproved(Long taskId, Long bizId, String comment);

    /** 审批驳回回调（同事务）。 */
    void onRejected(Long taskId, Long bizId, String comment);
}
