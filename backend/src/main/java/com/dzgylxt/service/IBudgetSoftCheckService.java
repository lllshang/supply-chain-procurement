package com.dzgylxt.service;

import com.dzgylxt.vo.purchase.BudgetCheckResultVO;

import java.math.BigDecimal;

/**
 * 预算软校验（设计 §2.1 / §7.3）。
 *
 * <p><!-- D3: P3 升级为"校验+占用+拦截+升级审批"，本读数逻辑直接复用 -->。
 * 本阶段只读：读 {@code budget_line(amount−used)} 余额；不足→
 * {@code budgetStatus=2} + 提示；不拦截、不发升级审批、不写 used_amount。</p>
 */
public interface IBudgetSoftCheckService {

    /**
     * 校验部门年度预算余额是否覆盖金额。
     *
     * @param deptId    部门
     * @param year      预算年度（null 取当前年）
     * @param subjectId 预算科目（null 则汇总部门全部科目）
     * @param amount    申请金额
     * @return 校验结果（budgetStatus：1=通过 2=超预算）
     */
    BudgetCheckResultVO check(Long deptId, Integer year, Long subjectId, BigDecimal amount);
}
