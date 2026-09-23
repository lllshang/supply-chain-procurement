package com.dzgylxt.service;

import com.dzgylxt.entity.budget.BudgetLine;
import com.dzgylxt.entity.budget.BudgetOccupyLog;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 预算执行台账查询服务（P3 设计 T03；只读，写路径仍唯一走 {@link IBudgetOccupyService}）。
 */
public interface IBudgetExecutionService {

    /** 执行台账：某部门某年月度行（含年度行 period=0 汇总展示 Q1b）+ 执行率。 */
    List<Row> ledger(Long deptId, Integer year);

    /** 占用流水分页（行/业务/动作过滤）。 */
    IPage<BudgetOccupyLog> logs(long current, long size, Long budgetLineId,
                                com.dzgylxt.enums.BudgetBizType bizType, Long bizId);

    /** 人工校准：按 Σlog(占用−释放) 重算 used_amount（差值写 ADJUST log 保持守恒；仅运维用）。 */
    void recalibrate(Long budgetLineId);

    /** 台账行（含执行率）。 */
    class Row extends BudgetLine {
        /** 执行率 = used_amount / amount（%） */
        private java.math.BigDecimal execRate;

        public java.math.BigDecimal getExecRate() {
            return execRate;
        }

        public void setExecRate(java.math.BigDecimal execRate) {
            this.execRate = execRate;
        }
    }
}
