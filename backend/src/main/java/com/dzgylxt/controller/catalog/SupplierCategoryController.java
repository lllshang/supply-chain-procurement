package com.dzgylxt.controller.catalog;

import com.dzgylxt.common.R;
import com.dzgylxt.entity.catalog.SupplierCategory;
import com.dzgylxt.service.ISupplierCategoryService;
import com.dzgylxt.vo.common.CategoryTreeNodeVO;
import com.dzgylxt.vo.supplier.SupplierCategorySaveReqVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 供应商三级分类管理。 */
@RestController
@RequestMapping("/api/v1/suppliers/categories")
public class SupplierCategoryController {

    private static final String GUARD = "isAuthenticated() and @authz.hasAnyPerm(authentication)";

    private final ISupplierCategoryService supplierCategoryService;

    public SupplierCategoryController(ISupplierCategoryService supplierCategoryService) {
        this.supplierCategoryService = supplierCategoryService;
    }

    @PreAuthorize(GUARD)
    @GetMapping("/tree")
    public R<List<CategoryTreeNodeVO>> tree() {
        return R.ok(supplierCategoryService.tree());
    }

    @PreAuthorize(GUARD)
    @GetMapping("/list")
    public R<List<SupplierCategory>> list() {
        return R.ok(supplierCategoryService.list());
    }

    @PreAuthorize(GUARD)
    @GetMapping("/{id}")
    public R<SupplierCategory> getById(@PathVariable Long id) {
        return R.ok(supplierCategoryService.getById(id));
    }

    @PreAuthorize(GUARD)
    @PostMapping
    public R<Long> create(@RequestBody SupplierCategorySaveReqVO req) {
        return R.ok(supplierCategoryService.createCategory(req));
    }

    @PreAuthorize(GUARD)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody SupplierCategorySaveReqVO req) {
        supplierCategoryService.updateCategory(id, req);
        return R.ok(true);
    }

    @PreAuthorize(GUARD)
    @PostMapping("/{id}/invalidate")
    public R<Boolean> invalidate(@PathVariable Long id) {
        supplierCategoryService.invalidate(id);
        return R.ok(true);
    }
}
