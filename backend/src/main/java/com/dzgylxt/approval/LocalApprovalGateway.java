package com.dzgylxt.approval;

import com.dzgylxt.common.BizException;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.approval.ApprovalTask;
import com.dzgylxt.enums.ApprovalStatus;
import com.dzgylxt.mapper.approval.ApprovalTaskMapper;
import com.dzgylxt.service.impl.approval.ApprovalTaskServiceImpl;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 本地自建审批中心实现——<b>P4 起降级为测试桩</b>（{@code @Profile("local-approval")}，
 * 生产上下文不再实例化；真实引擎见 {@link WorkflowApprovalGateway}）。
 *
 * <p>P2 契约（设计 §3）：</p>
 * <ol>
 *   <li><b>回调幂等</b>：仅 {@code CREATED / IN_PROGRESS} 任务可流转，
 *       终态（APPROVED/REJECTED/CALLBACK_DONE）重复回调直接忽略；</li>
 *   <li><b>重提 = 新任务</b>：同一 bizId 重新 {@link #create} 生成新任务，
 *       旧任务保留留痕，业务侧以 biz_type+biz_id 取最新任务；</li>
 *   <li><b>回调事务性</b>：任务状态流转与业务状态机回写同事务
 *       （业务处理器经 {@link ApprovalCallbackHandler} 按.bizType 分发，
 *       {@code ObjectProvider} 延迟解析避免与业务 Service 的循环依赖）。</li>
 * </ol>
 *
 * <p>P4 变更（设计 §2.1）：{@code @Profile("local-approval")} 桩化；原 PURCHASE_APPLY
 * 两级节点特判<b>删除</b>（语义由真实引擎按 approval_node_def.seq 通用承载）。</p>
 */
@Service
@Profile("local-approval")
public class LocalApprovalGateway implements ApprovalGateway {

    /** 单级审批的终态节点。 */
    public static final String NODE_END = "end";

    private final ApprovalTaskServiceImpl approvalTaskService;
    private final ApprovalTaskMapper approvalTaskMapper;
    private final ObjectProvider<ApprovalCallbackHandler> handlers;

    public LocalApprovalGateway(ApprovalTaskServiceImpl approvalTaskService,
                                ApprovalTaskMapper approvalTaskMapper,
                                ObjectProvider<ApprovalCallbackHandler> handlers) {
        this.approvalTaskService = approvalTaskService;
        this.approvalTaskMapper = approvalTaskMapper;
        this.handlers = handlers;
    }

    @Override
    public Long create(ApprovalTaskSpec spec) {
        ApprovalTask task = new ApprovalTask();
        task.setBizType(spec.getBizType());
        task.setBizId(spec.getBizId());
        task.setFlowKey(spec.getBizType());
        task.setStatus(ApprovalStatus.CREATED);
        task.setCurrentNode(NODE_END);
        task.setPayloadJson(spec.getPayloadJson());
        task.setRemark(spec.getTitle());
        approvalTaskService.save(task);
        return task.getId();
    }

    /**
     * 审批结论回调：幂等流转任务状态并分发业务处理器（同事务）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void callback(Long taskId, ApprovalDecision decision, String comment) {
        ApprovalTask task = approvalTaskMapper.selectById(taskId);
        if (task == null) {
            // QA #29：不存在的任务必须显式失败（原静默返回造成"幽灵成功"，
            // 与前端 ID 精度问题叠加时 UI 显示成功而 DB 无流转）
            throw new BizException(ResultCode.NOT_FOUND, "审批任务不存在：" + taskId);
        }
        // 幂等：仅 CREATED / IN_PROGRESS 可流转，终态重复回调忽略（不重复推进业务状态机）
        if (task.getStatus() != ApprovalStatus.CREATED
                && task.getStatus() != ApprovalStatus.IN_PROGRESS) {
            return;
        }
        boolean approved = decision == ApprovalDecision.APPROVED;
        task.setStatus(approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        task.setRemark(comment);
        approvalTaskMapper.updateById(task);

        ApprovalCallbackHandler handler = findHandler(task.getBizType());
        if (handler == null) {
            return;
        }
        if (approved) {
            handler.onApproved(taskId, task.getBizId(), comment);
        } else {
            handler.onRejected(taskId, task.getBizId(), comment);
        }
    }

    /** 按 bizType 查找业务回调处理器（无注册时返回 null，仅流转任务本身）。 */
    private ApprovalCallbackHandler findHandler(String bizType) {
        Map<String, ApprovalCallbackHandler> map = new HashMap<>();
        for (ApprovalCallbackHandler h : handlers) {
            map.putIfAbsent(h.bizType(), h);
        }
        return map.get(bizType);
    }
}
