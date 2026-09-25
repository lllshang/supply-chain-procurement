package com.dzgylxt.approval;

import com.dzgylxt.entity.order.PurchaseOrder;
import com.dzgylxt.mapper.order.PurchaseOrderMapper;
import com.dzgylxt.security.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 日常采购超授权确认回调处理器（P4 设计 §2.8 缺口收敛——DAILY_AUTH 业务动作补齐）。
 *
 * <p><b>背景（偏差③）</b>：D14 超单笔授权额度订单创建 DAILY_AUTH 审批任务后无任何消费点
 * （grep 实证零 handler）。本处理器补齐通过/驳回业务动作：</p>
 * <ul>
 *   <li><b>通过 = 授权确认生效</b>：清除订单 {@code auth_over_limit} 挂起标记（1→0，
 *       语义=部门负责人已确认该超限金额的日常采购授权），订单本身维持既有状态机继续履约；
 *       确认人/意见由 approval_record + 站内通知留痕（申请人=订单授权人 authorizedName）。</li>
 *   <li><b>驳回 = 订单终止 + 预算/合同额度释放</b>：复用既有 {@code cancelOrder} 链
 *       （合同额度回冲 + budget_occupy releaseCmd 守恒回冲 + 订单置 CANCELLED），
 *       同事务执行；仅 CREATED 状态可终止（已到货订单不做破坏性终止，WARN 放行）。</li>
 * </ul>
 */
@Slf4j
@Component
public class DailyAuthApprovalHandler implements ApprovalCallbackHandler {

    private final PurchaseOrderMapper purchaseOrderMapper;
    /** ObjectProvider 延迟解析：防 OrderServiceImpl → Gateway → Handler → IOrderService 循环依赖。 */
    private final ObjectProvider<com.dzgylxt.service.IOrderService> orderServiceProvider;

    public DailyAuthApprovalHandler(PurchaseOrderMapper purchaseOrderMapper,
                                    ObjectProvider<com.dzgylxt.service.IOrderService> orderServiceProvider) {
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.orderServiceProvider = orderServiceProvider;
    }

    @Override
    public String bizType() {
        return "DAILY_AUTH";
    }

    @Override
    public void onApproved(Long taskId, Long bizId, String comment) {
        if (bizId == null) {
            log.warn("[P4-DailyAuthHandler] 任务缺 bizId（订单 id），跳过 taskId={}", taskId);
            return;
        }
        PurchaseOrder order = purchaseOrderMapper.selectById(bizId);
        if (order == null) {
            log.warn("[P4-DailyAuthHandler] 订单不存在，跳过 taskId={} bizId={}", taskId, bizId);
            return;
        }
        // 授权确认生效：清除 auth_over_limit 挂起标记（语义=超限金额已经部门负责人确认）
        if (order.getAuthOverLimit() != null && order.getAuthOverLimit() == 1) {
            order.setAuthOverLimit(0);
            // 授权确认留痕（订单域内冗余 trail；完整审计见 approval_record）
            String confirmTrail = "超授权已确认(任务" + taskId + ",确认人"
                    + safeName() + (comment == null || comment.isBlank() ? "" : ",意见:" + comment) + ")";
            order.setRemark(order.getRemark() == null || order.getRemark().isBlank()
                    ? confirmTrail : order.getRemark() + "；" + confirmTrail);
            purchaseOrderMapper.updateById(order);
        }
    }

    @Override
    public void onRejected(Long taskId, Long bizId, String comment) {
        if (bizId == null) {
            log.warn("[P4-DailyAuthHandler] 任务缺 bizId（订单 id），跳过 taskId={}", taskId);
            return;
        }
        PurchaseOrder order = purchaseOrderMapper.selectById(bizId);
        if (order == null) {
            log.warn("[P4-DailyAuthHandler] 订单不存在，跳过 taskId={} bizId={}", taskId, bizId);
            return;
        }
        // 已到货订单不做破坏性终止（履约中），仅 WARN（登记业务确认：驳回后是否允许重新下单另行拍板）
        if (order.getStatus() == null
                || order.getStatus() != com.dzgylxt.enums.OrderStatus.CREATED) {
            log.warn("[P4-DailyAuthHandler] 订单非 CREATED 状态（{}），不做终止，仅留痕 orderId={} taskId={}",
                    order.getStatus(), bizId, taskId);
            return;
        }
        String reason = "超授权确认驳回(任务" + taskId + ")"
                + (comment == null || comment.isBlank() ? "" : "：" + comment);
        // 复用既有取消链：合同额度回冲 + budget_occupy 释放 + 订单置 CANCELLED（同事务）
        orderServiceProvider.getObject().cancelOrder(bizId, reason);
    }

    private String safeName() {
        String name = UserContext.getCurrentUsername();
        return name == null ? "system" : name;
    }
}
