package com.dzgylxt.vo.purchase;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 预算软校验结果（设计 §2.1 / §7.3 <!-- D3 -->）。
 *
 * <p>本阶段只读：budget_line 余额不足时 {@code budgetStatus=2} 仅提示、不拦截、
 * 不写 used_amount；P3 升级为"校验+占用+拦截+升级审批"时读数逻辑直接复用。</p>
 */
@Data
public class BudgetCheckResultVO implements Serializable {

    /** 1=通过 2=超预算（写入 purchase_apply.budget_status） */
    private Integer budgetStatus;
    /** 申请金额 */
    private BigDecimal amount;
    /** 预算余额（budget_line Σamount − Σused；无预算数据时为 null） */
    private BigDecimal balance;
    /** 提示信息 */
    private String message;
}
