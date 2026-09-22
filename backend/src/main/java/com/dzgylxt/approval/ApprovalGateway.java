package com.dzgylxt.approval;

/**
 * 审批网关抽象接口。
 *
 * <p>一期实现 {@link LocalApprovalGateway}（调本地自建审批单据中心）；后续可新增
 * {@code RemoteApprovalGateway} 对接企业审批中心，业务侧无感。
 * 所有需要审批的实体（采购申请/合同/定标/预算升级）统一经此接口发起，
 * 禁止业务代码直接依赖 {@code ApprovalTaskServiceImpl}。</p>
 */
public interface ApprovalGateway {

    /**
     * 发起审批任务，返回审批任务 id。
     */
    Long create(ApprovalTaskSpec spec);

    /**
     * 接收审批结果回调，回流业务状态机。
     */
    void callback(Long taskId, ApprovalDecision decision, String comment);
}
