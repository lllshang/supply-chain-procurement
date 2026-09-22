package com.dzgylxt.approval;

import com.dzgylxt.common.BizException;
import com.dzgylxt.mapper.approval.ApprovalTaskMapper;
import com.dzgylxt.service.impl.approval.ApprovalTaskServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审批 Gateway 回调契约单测（QA #29）：任务不存在必须显式失败（4040），
 * 不允许"幽灵成功"；幂等语义（终态重复回调忽略）保持不变。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocalApprovalGatewayTest {

    @Mock
    private ApprovalTaskServiceImpl approvalTaskService;

    @Mock
    private ApprovalTaskMapper approvalTaskMapper;

    private LocalApprovalGateway gateway;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ApprovalCallbackHandler> providers = mock(ObjectProvider.class);
        lenient().when(providers.iterator()).thenReturn(List.<ApprovalCallbackHandler>of().iterator());
        gateway = new LocalApprovalGateway(approvalTaskService, approvalTaskMapper, providers);
    }

    /** #29：不存在的任务回调 → 4040 BizException，而非静默成功。 */
    @Test
    void callback_missingTask_throwsNotFound() {
        when(approvalTaskMapper.selectById(999999L)).thenReturn(null);
        BizException e = assertThrows(BizException.class,
                () -> gateway.callback(999999L, ApprovalDecision.APPROVED, "ok"));
        assertEquals(4040, e.getCode());
    }

    /** 幂等：终态任务重复回调仍忽略（不抛错、不推进）。 */
    @Test
    void callback_terminalTask_ignored() {
        com.dzgylxt.entity.approval.ApprovalTask task = new com.dzgylxt.entity.approval.ApprovalTask();
        task.setId(1L);
        task.setBizType("AWARD");
        task.setBizId(2L);
        task.setStatus(com.dzgylxt.enums.ApprovalStatus.APPROVED);
        when(approvalTaskMapper.selectById(1L)).thenReturn(task);

        gateway.callback(1L, ApprovalDecision.APPROVED, "repeat");

        verify(approvalTaskMapper, never()).updateById(any(com.dzgylxt.entity.approval.ApprovalTask.class));
    }
}
