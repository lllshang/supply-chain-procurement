package com.dzgylxt.approval;

import com.dzgylxt.entity.approval.ApprovalTask;
import com.dzgylxt.enums.ApprovalStatus;
import com.dzgylxt.mapper.approval.ApprovalTaskMapper;
import com.dzgylxt.service.impl.approval.ApprovalTaskServiceImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 本地自建审批中心实现（一期）。后续可新增 {@code RemoteApprovalGateway} 对接企业审批中心。
 */
@Service
@Primary
public class LocalApprovalGateway implements ApprovalGateway {

    private final ApprovalTaskServiceImpl approvalTaskService;
    private final ApprovalTaskMapper approvalTaskMapper;

    public LocalApprovalGateway(ApprovalTaskServiceImpl approvalTaskService,
                                ApprovalTaskMapper approvalTaskMapper) {
        this.approvalTaskService = approvalTaskService;
        this.approvalTaskMapper = approvalTaskMapper;
    }

    @Override
    public Long create(ApprovalTaskSpec spec) {
        ApprovalTask task = new ApprovalTask();
        task.setBizType(spec.getBizType());
        task.setBizId(spec.getBizId());
        task.setFlowKey(spec.getBizType());
        task.setStatus(ApprovalStatus.CREATED);
        task.setCurrentNode("start");
        task.setRemark(spec.getTitle());
        approvalTaskService.save(task);
        return task.getId();
    }

    @Override
    public void callback(Long taskId, ApprovalDecision decision, String comment) {
        ApprovalTask task = approvalTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        task.setStatus(decision == ApprovalDecision.APPROVED
                ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        task.setRemark(comment);
        approvalTaskMapper.updateById(task);
    }
}
