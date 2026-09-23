package com.dzgylxt.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dzgylxt.entity.budget.BudgetLine;
import com.dzgylxt.mapper.budget.BudgetLineMapper;
import com.dzgylxt.mapper.budget.BudgetOccupyLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * P3 预算对账任务（设计 §3 合规硬规则）：每日 02:30 校验
 * {@code Σ budget_occupy_log(占用−释放) == budget_line.used_amount}。
 *
 * <p>不一致 = 占用入口被绕过或流水缺失（守恒被破坏），立即 ERROR 告警并输出
 * 差异行清单（Q6：提醒不阻塞业务；修复动作为人工核对，任务不代修）。</p>
 */
@Component
public class BudgetReconcileTask {

    private static final Logger log = LoggerFactory.getLogger(BudgetReconcileTask.class);

    @Autowired
    private BudgetLineMapper budgetLineMapper;

    @Autowired
    private BudgetOccupyLogMapper occupyLogMapper;

    /** 每日 02:30 对账（多实例部署时由调度中心单点触发）。 */
    @Scheduled(cron = "0 30 2 * * ?")
    public void reconcile() {
        List<BudgetLine> lines = budgetLineMapper.selectList(
                Wrappers.<BudgetLine>lambdaQuery().eq(BudgetLine::getDeleted, 0));
        List<String> mismatches = new ArrayList<>();
        for (BudgetLine line : lines) {
            BigDecimal used = line.getUsedAmount() == null ? BigDecimal.ZERO : line.getUsedAmount();
            BigDecimal net = occupyLogMapper.sumNetOccupiedByLine(line.getId());
            if (used.compareTo(net) != 0) {
                mismatches.add("行" + line.getId() + ": used=" + used
                        + " Σlog=" + net + " 差=" + used.subtract(net));
            }
        }
        if (mismatches.isEmpty()) {
            log.info("[预算对账] 全部 {} 行 Σlog == used_amount，守恒通过", lines.size());
        } else {
            log.error("[预算对账] 守恒被破坏！{} 行不一致：{}", mismatches.size(), mismatches);
        }
    }
}
