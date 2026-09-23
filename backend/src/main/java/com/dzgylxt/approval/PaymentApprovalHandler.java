package com.dzgylxt.approval;

import com.dzgylxt.service.IPaymentService;
import com.dzgylxt.service.impl.settlement.PaymentServiceImpl;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * PAYMENT 审批回调处理器（P3 设计 §4，财务审核阶段）。
 *
 * <p>通过 → reviewed_by/at 落（仍 UNPAID 待付款登记确认）；驳回 → REJECTED
 * （可修改重提）。业务逻辑集中在 {@link IPaymentService} 同事务委托。</p>
 */
@Component
public class PaymentApprovalHandler implements ApprovalCallbackHandler {

    private final PaymentServiceImpl paymentService;

    public PaymentApprovalHandler(PaymentServiceImpl paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public String bizType() {
        return "PAYMENT";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long taskId, Long bizId, String comment) {
        paymentService.handleApproved(taskId, bizId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long taskId, Long bizId, String comment) {
        paymentService.handleRejected(taskId, bizId);
    }
}
