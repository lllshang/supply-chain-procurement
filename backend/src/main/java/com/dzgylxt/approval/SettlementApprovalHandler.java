package com.dzgylxt.approval;

import com.dzgylxt.service.ISettlementService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * SETTLEMENT 审批回调处理器（P3 设计 §4）。
 *
 * <p>通过 → 结算单 SETTLED + 预算核销（writeOff）+ 订单全部结清→SETTLED；
 * 驳回 → 保持 PENDING 留痕可重提（规格口径，不改枚举值）。业务逻辑集中在
 * {@link ISettlementService#handleApproval}（同事务委托）。</p>
 */
@Component
public class SettlementApprovalHandler implements ApprovalCallbackHandler {

    private final ISettlementService settlementService;

    public SettlementApprovalHandler(ISettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @Override
    public String bizType() {
        return "SETTLEMENT";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onApproved(Long taskId, Long bizId, String comment) {
        settlementService.handleApproval(taskId, bizId, true, comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onRejected(Long taskId, Long bizId, String comment) {
        settlementService.handleApproval(taskId, bizId, false, comment);
    }
}
