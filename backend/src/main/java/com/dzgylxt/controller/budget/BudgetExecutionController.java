package com.dzgylxt.controller.budget;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dzgylxt.common.R;
import com.dzgylxt.entity.budget.BudgetOccupyLog;
import com.dzgylxt.enums.BudgetBizType;
import com.dzgylxt.service.IBudgetExecutionService;
import com.dzgylxt.service.IBudgetOccupyService;
import com.dzgylxt.vo.budget.BudgetOccupyCmd;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 预算执行台账（P3 设计 §5 /api/v1/budgets/execution；T03）。
 *
 * <p>台账只读；月度调整走 {@code POST /adjust}（超 20% 自动升级 BUDGET 审批）；
 * {@code recalibrate} 为运维校准入口（差值补 ADJUST log 保持守恒）。</p>
 */
@RestController
@RequestMapping("/api/v1/budgets/execution")
public class BudgetExecutionController {

    @Autowired
    private IBudgetExecutionService executionService;

    @Autowired
    private IBudgetOccupyService budgetOccupyService;

    /** 执行台账（部门×年度，月度行+执行率）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping
    public R<List<IBudgetExecutionService.Row>> ledger(
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Integer year) {
        return R.ok(executionService.ledger(deptId, year));
    }

    /** 占用流水分页。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/logs")
    public R<IPage<BudgetOccupyLog>> logs(@RequestParam(defaultValue = "1") long current,
                                          @RequestParam(defaultValue = "10") long size,
                                          @RequestParam(required = false) Long budgetLineId,
                                          @RequestParam(required = false) BudgetBizType bizType,
                                          @RequestParam(required = false) Long bizId) {
        return R.ok(executionService.logs(current, size, budgetLineId, bizType, bizId));
    }

    /** 月度调整（调增直生效；调减校验 amount≥used；超 20% 走 BUDGET 审批）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping("/{lineId}/adjust")
    public R<Boolean> adjust(@PathVariable Long lineId, @RequestBody AdjustReq req) {
        boolean pending = budgetOccupyService.adjustAmount(lineId, req.getNewAmount(), req.getReason());
        return R.ok(pending);
    }

    /** 人工校准（运维）：按 Σlog 重算 used_amount。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping("/{lineId}/recalibrate")
    public R<Boolean> recalibrate(@PathVariable Long lineId) {
        executionService.recalibrate(lineId);
        return R.ok(true);
    }

    /** 调整请求体。 */
    public static class AdjustReq {
        private java.math.BigDecimal newAmount;
        private String reason;

        public java.math.BigDecimal getNewAmount() {
            return newAmount;
        }

        public void setNewAmount(java.math.BigDecimal newAmount) {
            this.newAmount = newAmount;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
