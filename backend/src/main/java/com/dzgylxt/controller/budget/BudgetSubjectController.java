package com.dzgylxt.controller.budget;

import com.dzgylxt.common.R;
import com.dzgylxt.entity.budget.BudgetSubject;
import com.dzgylxt.service.IBudgetSubjectService;
import com.dzgylxt.vo.budget.BudgetSubjectSaveReqVO;
import com.dzgylxt.vo.common.CategoryTreeNodeVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 预算科目管理。 */
@RestController
@RequestMapping("/api/v1/budgets/subjects")
public class BudgetSubjectController {

    private static final String GUARD = "isAuthenticated() and @authz.hasAnyPerm(authentication)";

    private final IBudgetSubjectService budgetSubjectService;

    public BudgetSubjectController(IBudgetSubjectService budgetSubjectService) {
        this.budgetSubjectService = budgetSubjectService;
    }

    @PreAuthorize(GUARD)
    @GetMapping("/tree")
    public R<List<CategoryTreeNodeVO>> tree() {
        return R.ok(budgetSubjectService.tree());
    }

    @PreAuthorize(GUARD)
    @GetMapping("/list")
    public R<List<BudgetSubject>> list() {
        return R.ok(budgetSubjectService.list());
    }

    @PreAuthorize(GUARD)
    @GetMapping("/{id}")
    public R<BudgetSubject> getById(@PathVariable Long id) {
        return R.ok(budgetSubjectService.getById(id));
    }

    @PreAuthorize(GUARD)
    @PostMapping
    public R<Long> create(@RequestBody BudgetSubjectSaveReqVO req) {
        return R.ok(budgetSubjectService.createSubject(req));
    }

    @PreAuthorize(GUARD)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody BudgetSubjectSaveReqVO req) {
        budgetSubjectService.updateSubject(id, req);
        return R.ok(true);
    }

    @PreAuthorize(GUARD)
    @PostMapping("/{id}/invalidate")
    public R<Boolean> invalidate(@PathVariable Long id) {
        budgetSubjectService.invalidate(id);
        return R.ok(true);
    }
}
