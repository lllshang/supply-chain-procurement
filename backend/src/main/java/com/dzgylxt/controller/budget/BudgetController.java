package com.dzgylxt.controller.budget;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dzgylxt.common.PageResult;
import com.dzgylxt.common.R;
import com.dzgylxt.service.IBudgetImportService;
import com.dzgylxt.service.IBudgetQueryService;
import com.dzgylxt.vo.budget.BudgetHeaderQueryReqVO;
import com.dzgylxt.vo.budget.BudgetHeaderRespVO;
import com.dzgylxt.vo.budget.BudgetImportPreviewVO;
import com.dzgylxt.vo.budget.BudgetLineRespVO;
import com.dzgylxt.vo.common.ImportTaskVO;
import com.dzgylxt.common.SecurityConstants;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 年度预算导入 / 预算头与台账查询。 */
@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {


    private final IBudgetImportService budgetImportService;
    private final IBudgetQueryService budgetQueryService;

    public BudgetController(IBudgetImportService budgetImportService,
                            IBudgetQueryService budgetQueryService) {
        this.budgetImportService = budgetImportService;
        this.budgetQueryService = budgetQueryService;
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping("/import/preview")
    public R<BudgetImportPreviewVO> preview(@RequestParam("file") MultipartFile file) {
        return R.ok(budgetImportService.preview(file));
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping("/import")
    public R<ImportTaskVO> importAnnual(@RequestParam("file") MultipartFile file) {
        return R.ok(budgetImportService.importAnnual(file));
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/import/tasks/{taskId}")
    public R<ImportTaskVO> taskStatus(@PathVariable String taskId) {
        return R.ok(budgetImportService.taskStatus(taskId));
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/headers")
    public R<PageResult<BudgetHeaderRespVO>> headers(BudgetHeaderQueryReqVO req) {
        IPage<BudgetHeaderRespVO> page = budgetQueryService.pageHeader(req);
        return R.ok(PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/{headerId}/lines")
    public R<List<BudgetLineRespVO>> lines(@PathVariable Long headerId) {
        return R.ok(budgetQueryService.listLines(headerId));
    }
}
