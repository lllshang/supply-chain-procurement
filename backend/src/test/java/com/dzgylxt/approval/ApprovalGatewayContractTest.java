package com.dzgylxt.approval;

import com.dzgylxt.common.BizException;
import com.dzgylxt.entity.approval.ApprovalFlowDef;
import com.dzgylxt.entity.approval.ApprovalNode;
import com.dzgylxt.entity.approval.ApprovalNodeDef;
import com.dzgylxt.entity.approval.ApprovalTask;
import com.dzgylxt.approval.ApprovalFlowConfigService.CachedFlow;
import com.dzgylxt.enums.ApprovalStatus;
import com.dzgylxt.mapper.approval.ApprovalNodeMapper;
import com.dzgylxt.mapper.approval.ApprovalRecordMapper;
import com.dzgylxt.mapper.approval.ApprovalTaskMapper;
import com.dzgylxt.security.LoginUser;
import com.dzgylxt.service.INoticeService;
import com.dzgylxt.service.impl.approval.ApprovalTaskServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P4 Gateway 契约测试（设计 §8-2）：Local 桩与 Workflow 真实引擎<b>双实现参数化</b>全绿——
 * 幽灵任务显式失败（4040）/ 终态幂等忽略 / 通过终态分发 handler / 重提=新任务。
 * 契约签名（create/callback/ApprovalTaskSpec/ApprovalDecision）零变更由本测试锁定。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApprovalGatewayContractTest {

    @Mock
    private ApprovalTaskServiceImpl approvalTaskService;
    @Mock
    private ApprovalTaskMapper approvalTaskMapper;
    @Mock
    private ApprovalNodeMapper approvalNodeMapper;
    @Mock
    private ApprovalRecordMapper approvalRecordMapper;
    @Mock
    private ApprovalFlowConfigService flowConfigService;
    @Mock
    private ApproverResolver approverResolver;
    @Mock
    private INoticeService noticeService;
    @Mock
    private ApprovalCallbackHandler handler;

    private LocalApprovalGateway localGateway;
    private WorkflowApprovalGateway workflowGateway;
    private java.util.Deque<Long> assignedIds;

    @BeforeEach
    void setUp() {
        localGateway = new LocalApprovalGateway(approvalTaskService, approvalTaskMapper, providers());
        workflowGateway = new WorkflowApprovalGateway(flowConfigService, approverResolver,
                approvalTaskService, approvalTaskMapper, approvalNodeMapper, approvalRecordMapper,
                providers(), noticeService);

        assignedIds = new java.util.concurrent.ConcurrentLinkedDeque<>();
        assignedIds.add(1001L);
        assignedIds.add(1002L);
        // create 落库时回填 id（重提=新任务契约断言的数据基础）
        doAnswer(inv -> {
            ApprovalTask t = inv.getArgument(0);
            Long assigned = assignedIds.pollFirst();
            t.setId(assigned != null ? assigned : 9000L + System.nanoTime() % 1000);
            return true;
        }).when(approvalTaskService).save(any(ApprovalTask.class));

        // Workflow 引擎：单节点流程种子（PURCHASE_APPLY 简化链：N1 恒生效）
        stubWorkflowConfig();
        setUser(77L, "approver");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void stubWorkflowConfig() {
        ApprovalFlowDef flowDef = new ApprovalFlowDef();
        flowDef.setFlowKey("PURCHASE_APPLY");
        flowDef.setBizType("PURCHASE_APPLY");
        flowDef.setFlowVersion(1);
        flowDef.setEnabled(1);
        ApprovalNodeDef nodeDef = new ApprovalNodeDef();
        nodeDef.setFlowKey("PURCHASE_APPLY");
        nodeDef.setNodeCode("N1");
        nodeDef.setNodeName("部门负责人审批");
        nodeDef.setSeq(1);
        nodeDef.setApproverType(ApproverResolver.TYPE_ROLE);
        nodeDef.setApproverValue("PURCHASE_DEPT");
        nodeDef.setSignType("ANY");
        nodeDef.setEnabled(1);
        CachedFlow flow = new CachedFlow();
        flow.setFlowDef(flowDef);
        flow.setNodeDefs(List.of(nodeDef));
        lenient().when(flowConfigService.getFlow("PURCHASE_APPLY")).thenReturn(flow);
        lenient().when(flowConfigService.listNodes("PURCHASE_APPLY")).thenReturn(List.of(nodeDef));
        lenient().when(approverResolver.resolveCandidates(any(), any())).thenReturn(Set.of(77L));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<ApprovalCallbackHandler> providers() {
        ObjectProvider<ApprovalCallbackHandler> p = mock(ObjectProvider.class);
        lenient().when(p.iterator()).thenReturn(List.<ApprovalCallbackHandler>of().iterator());
        return p;
    }

    private void setUser(Long id, String username) {
        LoginUser u = new LoginUser();
        u.setId(id);
        u.setUsername(username);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u, null, List.of()));
    }

    private ApprovalTaskSpec spec() {
        ApprovalTaskSpec s = new ApprovalTaskSpec();
        s.setBizType("PURCHASE_APPLY");
        s.setBizId(88L);
        s.setTitle("测试审批单");
        s.setApplicant("applicant01");
        s.setPayloadJson("{\"amount\":1000}");
        return s;
    }

    /** 为 Workflow 引擎的在途任务打节点快照查询桩。 */
    private void stubNodeSnapshot(long taskId, String nodeCode) {
        ApprovalNode node = new ApprovalNode();
        node.setId(500L + taskId);
        node.setTaskId(taskId);
        node.setNodeCode(nodeCode);
        node.setSeq(1);
        node.setSignType("ANY");
        node.setStatus(WorkflowApprovalGateway.NODE_PENDING);
        node.setVersion(0);
        lenient().when(approvalNodeMapper.selectList(any())).thenReturn(List.of(node));
        lenient().when(approvalNodeMapper.casComplete(any(), any(), any(), anyString(), any()))
                .thenReturn(1);
        lenient().when(approvalTaskMapper.casTransition(any(), any(), any(), any(), any()))
                .thenReturn(1);
    }

    private ApprovalTask inFlightTask(long taskId, String bizType) {
        ApprovalTask task = new ApprovalTask();
        task.setId(taskId);
        task.setBizType(bizType);
        task.setBizId(88L);
        task.setFlowKey(bizType);
        task.setStatus(ApprovalStatus.IN_PROGRESS);
        task.setCurrentNode("N1");
        task.setRemark("测试审批单");
        task.setApplicant("applicant01");
        task.setApplicantId(66L);
        task.setVersion(0);
        return task;
    }

    // ==================== 双实现参数化契约 ====================

    /** 契约①：幽灵任务显式失败（4040），不允许"幽灵成功"（QA #29 延续）。 */
    @Test
    void callback_missingTask_throwsNotFound_both() {
        when(approvalTaskMapper.selectById(999999L)).thenReturn(null);
        BizException e1 = assertThrows(BizException.class,
                () -> localGateway.callback(999999L, ApprovalDecision.APPROVED, "ok"));
        assertEquals(4040, e1.getCode());
        BizException e2 = assertThrows(BizException.class,
                () -> workflowGateway.callback(999999L, ApprovalDecision.APPROVED, "ok"));
        assertEquals(4040, e2.getCode());
    }

    /** 契约②：终态幂等——重复回调静默忽略，不重复推进业务状态机。 */
    @Test
    void callback_terminalTask_ignored_both() {
        ApprovalTask task = inFlightTask(1L, "PURCHASE_APPLY");
        task.setStatus(ApprovalStatus.APPROVED);
        when(approvalTaskMapper.selectById(1L)).thenReturn(task);

        localGateway.callback(1L, ApprovalDecision.APPROVED, "repeat");
        workflowGateway.callback(1L, ApprovalDecision.APPROVED, "repeat");

        verify(approvalTaskMapper, never()).updateById(any(ApprovalTask.class));
    }

    /** 契约③：单节点通过 → 终态 APPROVED（Local 直转 / Workflow 节点条件更新+终态）。 */
    @Test
    void approve_singleNode_terminalApproved_both() {
        // Local：任务存在即直转
        ApprovalTask localTask = inFlightTask(1L, "PURCHASE_APPLY");
        localTask.setStatus(ApprovalStatus.CREATED);
        localTask.setCurrentNode("end");
        when(approvalTaskMapper.selectById(1L)).thenReturn(localTask);
        localGateway.callback(1L, ApprovalDecision.APPROVED, "ok");
        assertEquals(ApprovalStatus.APPROVED, localTask.getStatus());

        // Workflow：节点快照 + 条件更新 + record 留痕
        ApprovalTask wfTask = inFlightTask(2L, "PURCHASE_APPLY");
        when(approvalTaskMapper.selectById(2L)).thenReturn(wfTask);
        stubNodeSnapshot(2L, "N1");
        workflowGateway.callback(2L, ApprovalDecision.APPROVED, "ok");
        verify(approvalTaskMapper).casTransition(eq(2L), eq(0), eq(ApprovalStatus.APPROVED),
                any(), any());
        verify(approvalRecordMapper).insert(any(com.dzgylxt.entity.approval.ApprovalRecord.class));
    }

    /** 契约④：重提=新任务——同一 bizId 重新 create 生成新任务（旧任务留痕可查）。 */
    @Test
    void resubmit_createsNewTask_both() {
        Long id1 = localGateway.create(spec());
        Long id2 = localGateway.create(spec());
        assertNotEquals(id1, id2);

        Long id3 = workflowGateway.create(spec());
        Long id4 = workflowGateway.create(spec());
        assertNotEquals(id3, id4);
        // 引擎创建即 IN_PROGRESS 且首节点为当前节点
        verify(approvalTaskService, org.mockito.Mockito.times(4)).save(any(ApprovalTask.class));
    }

    /** 契约⑤（Workflow 特有）：驳回意见必填在网关内拦截（双拦截第二道）。 */
    @Test
    void reject_withoutComment_throws_workflow() {
        ApprovalTask wfTask = inFlightTask(3L, "PURCHASE_APPLY");
        when(approvalTaskMapper.selectById(3L)).thenReturn(wfTask);
        stubNodeSnapshot(3L, "N1");
        BizException e = assertThrows(BizException.class,
                () -> workflowGateway.callback(3L, ApprovalDecision.REJECTED, " "));
        assertEquals(4000, e.getCode());
    }
}
