package com.dzgylxt.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dzgylxt.entity.contract.Contract;
import com.dzgylxt.entity.order.OrderItem;
import com.dzgylxt.entity.order.PurchaseOrder;
import com.dzgylxt.enums.ContractStatus;
import com.dzgylxt.enums.OrderStatus;
import com.dzgylxt.mapper.contract.ContractMapper;
import com.dzgylxt.mapper.order.OrderItemMapper;
import com.dzgylxt.mapper.order.PurchaseOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * P2 扫描与提醒定时任务（设计 P2-T09；Q6：站内 + 邮件，计划日前 1 天与逾期当日各一次，不阻塞业务）。
 *
 * <p>扫描范围：</p>
 * <ol>
 *   <li><b>到货逾期</b>：order_item.plan_date &lt; today 且订单未 RECEIVED/CANCELLED；</li>
 *   <li><b>合同到期</b>：EFFECTIVE 合同 valid_to 距今 ≤ 提前天数（默认 30，对齐 §2.5 派生口径）。</li>
 * </ol>
 *
 * <p>提醒渠道：站内（应用日志 WARN，供前端角标轮询扩展）+ 邮件（配置了
 * {@code spring.mail.host} 才发送；{@code app.p2.remind-mail-to} 为空则跳过）。
 * 扫描异常不影响业务主链路。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.p2.scan-enabled", havingValue = "true", matchIfMissing = true)
public class P2ScanReminderTask {

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private PurchaseOrderMapper orderMapper;

    @Autowired
    private ContractMapper contractMapper;

    @Autowired
    private org.springframework.beans.factory.ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.p2.remind-mail-to:}")
    private String remindMailTo;

    /** 每日 08:00 扫描（生产可由配置覆盖 cron）。 */
    @Scheduled(cron = "${app.p2.scan-cron:0 0 8 * * ?}")
    public void scanDaily() {
        try {
            int arrivalOverdue = scanArrivalOverdue();
            int contractExpiring = scanContractExpiring();
            if (arrivalOverdue + contractExpiring > 0) {
                sendMailIfConfigured(arrivalOverdue, contractExpiring);
            }
        } catch (Exception e) {
            // 提醒失败不阻塞业务（Q6）
            log.error("P2 扫描提醒任务执行失败", e);
        }
    }

    /** 到货逾期扫描：计划日期已过且订单未收货完成。 */
    private int scanArrivalOverdue() {
        LocalDate today = LocalDate.now();
        List<OrderItem> overdue = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .lt(OrderItem::getPlanDate, today)
                .isNotNull(OrderItem::getPlanDate));
        int count = 0;
        for (OrderItem item : overdue) {
            PurchaseOrder order = orderMapper.selectById(item.getOrderId());
            if (order == null || order.getStatus() == OrderStatus.RECEIVED
                    || order.getStatus() == OrderStatus.CANCELLED) {
                continue;
            }
            count++;
            log.warn("[P2提醒-到货逾期] 订单 {} 明细 {} 计划到货日 {} 已逾期",
                    order.getOrderNo(), item.getId(), item.getPlanDate());
        }
        if (count > 0) {
            log.warn("[P2提醒] 到货逾期明细共 {} 条", count);
        }
        return count;
    }

    /** 合同到期扫描：EFFECTIVE 且 valid_to 距今 ≤ 30 天（Q6 默认）。 */
    private int scanContractExpiring() {
        LocalDate today = LocalDate.now();
        List<Contract> contracts = contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getStatus, ContractStatus.EFFECTIVE));
        int count = 0;
        for (Contract contract : contracts) {
            if (contract.getValidTo() == null) {
                continue;
            }
            long days = java.time.temporal.ChronoUnit.DAYS.between(today, contract.getValidTo());
            if (days <= 30) {
                count++;
                log.warn("[P2提醒-合同到期] 合同 {} 将于 {} 到期（剩 {} 天）",
                        contract.getNo(), contract.getValidTo(), days);
            }
        }
        if (count > 0) {
            log.warn("[P2提醒] 即将到期合同共 {} 份", count);
        }
        return count;
    }

    /** 邮件提醒（仅在配置了 mail host 与收件人时发送）。 */
    private void sendMailIfConfigured(int arrivalOverdue, int contractExpiring) {
        if (remindMailTo == null || remindMailTo.isBlank()) {
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(remindMailTo.split(","));
            message.setSubject("【供应链系统】P2 采购提醒");
            message.setText("到货逾期明细 " + arrivalOverdue + " 条；即将到期合同 " + contractExpiring + " 份。请及时处理。");
            sender.send(message);
        } catch (Exception e) {
            log.warn("[P2提醒] 邮件发送失败（不阻塞业务）：{}", e.getMessage());
        }
    }
}
