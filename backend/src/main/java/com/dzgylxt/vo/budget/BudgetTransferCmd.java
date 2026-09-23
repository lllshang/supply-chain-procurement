package com.dzgylxt.vo.budget;

import com.dzgylxt.enums.BudgetBizType;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 预算占用主体转移命令（P3 设计 §2.3 行 6：申请→订单，金额不变）。
 *
 * <p>实现语义：源侧按余额逐行「释放」（action=RELEASE）+ 目标侧同行「占用」
 * （action=OCCUPY，biz_type=ORDER），同一事务、同一行、同额——保持
 * {@code Σlog(占用−释放) == used_amount} 恒等式与 used_amount 不变。
 * 源余额不足（如单价上调）时差额对同行强制占用（force，走超支语义）。</p>
 */
@Data
public class BudgetTransferCmd implements Serializable {
    /** 源业务类型（占用持有方，通常 APPLY） */
    private BudgetBizType fromBizType;
    private Long fromBizId;
    /** 目标业务类型（通常 ORDER） */
    private BudgetBizType toBizType;
    private Long toBizId;
    /** 转移金额 */
    private BigDecimal amount;
    private String remark;
}
