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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P4 并发防重审单测（规格 §3.4 AC①② / 设计 §2.6）：
 * ① 或签竞争——两并发 approve 同一任务 → 恰一成功一 3002(DATA_CONFLICT)；
 * ② approve/reject 并发 → 终态唯一（后到者 3002）；
 * ③ ALL 会签计数推进（候选未齐节点保持 PENDING）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowApprovalGatewayConcurrencyTest {

    private static final long TASK_ID = 42L;
    private static final long USER_A = 77L;
    private static final long USER_B = 78L;

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

    private WorkflowApprovalGateway gateway;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ApprovalCallbackHandler> providers = mock(ObjectProvider.class);
        lenient().when(providers.iterator()).thenReturn(List.<ApprovalCallbackHandler>of().iterator());
        gateway = new WorkflowApprovalGateway(flowConfigService, approverResolver,
                approvalTaskService, approvalTaskMapper, approvalNodeMapper, approvalRecordMapper,
                providers, noticeService);

        stubConfigAndTask();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void stubConfigAndTask() {
        ApprovalFlowDef flowDef = new ApprovalFlowDef();
        flowDef.setFlowKey("AWARD");
        flowDef.setEnabled(1);
        flowDef.setFlowVersion(1);
        ApprovalNodeDef nodeDef = new ApprovalNodeDef();
        nodeDef.setFlowKey("AWARD");
        nodeDef.setNodeCode("N1");
        nodeDef.setSeq(1);
        nodeDef.setApproverType(ApproverResolver.TYPE_ROLE);
        nodeDef.setApproverValue("PROCUREMENT_LEAD");
        nodeDef.setSignType("ANY");
        nodeDef.setEnabled(1);
        CachedFlow flow = new CachedFlow();
        flow.setFlowDef(flowDef);
        flow.setNodeDefs(List.of(nodeDef));
        lenient().when(flowConfigService.getFlow("AWARD")).thenReturn(flow);
        lenient().when(flowConfigService.listNodes("AWARD")).thenReturn(List.of(nodeDef));

        ApprovalTask task = new ApprovalTask();
        task.setId(TASK_ID);
        task.setBizType("AWARD");
        task.setBizId(8L);
        task.setFlowKey("AWARD");
        task.setStatus(ApprovalStatus.IN_PROGRESS);
        task.setCurrentNode("N1");
        task.setRemark("定标审批");
        task.setApplicantId(66L);
        task.setVersion(0);
        lenient().when(approvalTaskMapper.selectById(TASK_ID)).thenReturn(task);

        ApprovalNode node = new ApprovalNode();
        node.setId(500L);
        node.setTaskId(TASK_ID);
        node.setNodeCode("N1");
        node.setSeq(1);
        node.setSignType("ANY");
        node.setStatus(WorkflowApprovalGateway.NODE_PENDING);
        node.setVersion(0);
        lenient().when(approvalNodeMapper.selectList(any())).thenReturn(List.of(node));

        lenient().when(approverResolver.resolveCandidates(any(), any()))
                .thenReturn(Set.of(USER_A, USER_B));
    }

    private void setUser(Long id) {
        LoginUser u = new LoginUser();
        u.setId(id);
        u.setUsername("user" + id);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u, null, List.of()));
    }

    /** AC①：两并发 approve 同一任务（或签竞争）→ 恰一成功一 3002。 */
    @Test
    void concurrentApprove_exactlyOneWins_otherGetsDataConflict() throws Exception {
        setUser(USER_A);
        // 节点行条件更新只放行第一次调用（模拟 DB 行锁/版本竞争的真实语义）
        AtomicInteger nodeWins = new AtomicInteger();
        when(approvalNodeMapper.casComplete(anyLong(), anyInt(), anyLong(), anyString(), any()))
                .thenAnswer(inv -> nodeWins.incrementAndGet() == 1 ? 1 : 0);
        // 单节点任务：首个节点行完成后走终态 casTransition（放行）
        when(approvalTaskMapper.casTransition(anyLong(), anyInt(), any(), any(), any()))
                .thenReturn(1);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        java.util.List<Throwable> errors = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        Runnable actor = () -> {
            // SecurityContext 为 ThreadLocal：并发线程各自注入候选人身份
            setUser(USER_A);
            ready.countDown();
            try {
                go.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                gateway.callback(TASK_ID, ApprovalDecision.APPROVED, "ok");
                successes.incrementAndGet();
            } catch (BizException e) {
                if (e.getCode() != 3002) {
                    errors.add(new AssertionError("非冲突异常：" + e.getCode() + " " + e.getMessage()));
                } else {
                    conflicts.incrementAndGet();
                }
            } catch (Throwable t) {
                errors.add(t);
            }
        };
        Thread t1 = new Thread(actor);
        Thread t2 = new Thread(actor);
        t1.start();
        t2.start();
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        go.countDown();
        t1.join(5000);
        t2.join(5000);

        assertTrue(errors.isEmpty(), "线程内出现意外异常：" + errors);
        assertEquals(1, successes.get(), "或签竞争应恰一成功");
        assertEquals(1, conflicts.get(), "或签竞争败者应得 3002(DATA_CONFLICT)");
    }

    /** AC②：approve 与 reject 并发 → 终态唯一（task 条件更新后到者 3002）。 */
    @Test
    void concurrentApproveAndReject_terminalStateUnique() throws Exception {
        AtomicInteger taskWins = new AtomicInteger();
        when(approvalNodeMapper.casComplete(anyLong(), anyInt(), anyLong(), anyString(), any()))
                .thenReturn(1);
        when(approvalTaskMapper.casTransition(anyLong(), anyInt(), any(), any(), any()))
                .thenAnswer(inv -> taskWins.incrementAndGet() == 1 ? 1 : 0);

        setUser(USER_A);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger terminal = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        java.util.List<Throwable> errors = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        Thread approver = new Thread(() -> {
            setUser(USER_A);
            ready.countDown();
            try {
                go.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                gateway.callback(TASK_ID, ApprovalDecision.APPROVED, "ok");
                terminal.incrementAndGet();
            } catch (BizException e) {
                if (e.getCode() != 3002) {
                    errors.add(new AssertionError("非冲突异常：" + e.getCode() + " " + e.getMessage()));
                } else {
                    conflicts.incrementAndGet();
                }
            } catch (Throwable t) {
                errors.add(t);
            }
        });
        Thread rejector = new Thread(() -> {
            setUser(USER_A);
            ready.countDown();
            try {
                go.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                gateway.callback(TASK_ID, ApprovalDecision.REJECTED, "不同意");
                terminal.incrementAndGet();
            } catch (BizException e) {
                if (e.getCode() != 3002) {
                    errors.add(new AssertionError("非冲突异常：" + e.getCode() + " " + e.getMessage()));
                } else {
                    conflicts.incrementAndGet();
                }
            } catch (Throwable t) {
                errors.add(t);
            }
        });
        approver.start();
        rejector.start();
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        go.countDown();
        approver.join(5000);
        rejector.join(5000);

        assertTrue(errors.isEmpty(), "线程内出现意外异常：" + errors);
        assertEquals(1, terminal.get(), "终态唯一：恰一次流转成功");
        assertEquals(1, conflicts.get(), "后到者应得 3002(DATA_CONFLICT)");
    }

    /** ALL 会签：候选未齐 → 节点保持 PENDING、任务不推进。 */
    @Test
    void allSign_quorumNotReached_nodeStaysPending() {
        setUser(USER_A);
        when(approvalNodeMapper.casComplete(anyLong(), anyInt(), anyLong(), anyString(), any()))
                .thenReturn(1);
        // 已有 1 人 approve，候选 2 人 → 未齐
        when(approvalRecordMapper.selectCount(any())).thenReturn(1L);

        ApprovalNodeDef allNode = new ApprovalNodeDef();
        allNode.setFlowKey("AWARD");
        allNode.setNodeCode("N1");
        allNode.setSeq(1);
        allNode.setApproverType(ApproverResolver.TYPE_ROLE);
        allNode.setApproverValue("PROCUREMENT_LEAD");
        allNode.setSignType("ALL");
        allNode.setEnabled(1);
        when(flowConfigService.listNodes("AWARD")).thenReturn(List.of(allNode));
        ApprovalNode node = approvalNodeMapper.selectList(any()).get(0);
        node.setSignType("ALL");

        gateway.callback(TASK_ID, ApprovalDecision.APPROVED, "ok");

        // 任务不得推进/终态
        org.mockito.Mockito.verify(approvalTaskMapper, org.mockito.Mockito.never())
                .casTransition(anyLong(), anyInt(), any(), any(), any());
    }
}
