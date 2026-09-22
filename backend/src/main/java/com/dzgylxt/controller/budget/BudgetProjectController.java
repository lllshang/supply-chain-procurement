package com.dzgylxt.controller.budget;

import com.dzgylxt.common.R;
import com.dzgylxt.entity.budget.BudgetProject;
import com.dzgylxt.service.IBudgetProjectService;
import com.dzgylxt.vo.budget.BudgetProjectSaveReqVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 预算项目管理。 */
@RestController
@RequestMapping("/api/v1/budgets/projects")
public class BudgetProjectController {

    private static final String GUARD = "isAuthenticated() and @authz.hasAnyPerm(authentication)";

    private final IBudgetProjectService budgetProjectService;

    public BudgetProjectController(IBudgetProjectService budgetProjectService) {
        this.budgetProjectService = budgetProjectService;
    }

    @PreAuthorize(GUARD)
    @GetMapping("/list")
    public R<List<BudgetProject>> list(@RequestParam(required = false) Integer year) {
        return R.ok(budgetProjectService.listByYear(year));
    }

    @PreAuthorize(GUARD)
    @GetMapping("/{id}")
    public R<BudgetProject> getById(@PathVariable Long id) {
        return R.ok(budgetProjectService.getById(id));
    }

    @PreAuthorize(GUARD)
    @PostMapping
    public R<Long> create(@RequestBody BudgetProjectSaveReqVO req) {
        return R.ok(budgetProjectService.createProject(req));
    }

    @PreAuthorize(GUARD)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody BudgetProjectSaveReqVO req) {
        budgetProjectService.updateProject(id, req);
        return R.ok(true);
    }

    @PreAuthorize(GUARD)
    @PostMapping("/{id}/invalidate")
    public R<Boolean> invalidate(@PathVariable Long id) {
        budgetProjectService.invalidate(id);
        return R.ok(true);
    }
}
