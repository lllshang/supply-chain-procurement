package com.dzgylxt.controller.catalog;

import com.dzgylxt.common.R;
import com.dzgylxt.entity.catalog.SupplierCategory;
import com.dzgylxt.service.ISupplierCategoryService;
import com.dzgylxt.vo.common.CategoryTreeNodeVO;
import com.dzgylxt.vo.supplier.SupplierCategorySaveReqVO;
import com.dzgylxt.common.SecurityConstants;
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


    private final ISupplierCategoryService supplierCategoryService;

    public SupplierCategoryController(ISupplierCategoryService supplierCategoryService) {
        this.supplierCategoryService = supplierCategoryService;
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/tree")
    public R<List<CategoryTreeNodeVO>> tree() {
        return R.ok(supplierCategoryService.tree());
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/list")
    public R<List<SupplierCategory>> list() {
        return R.ok(supplierCategoryService.list());
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/{id}")
    public R<SupplierCategory> getById(@PathVariable Long id) {
        return R.ok(supplierCategoryService.getById(id));
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping
    public R<Long> create(@RequestBody SupplierCategorySaveReqVO req) {
        return R.ok(supplierCategoryService.createCategory(req));
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody SupplierCategorySaveReqVO req) {
        supplierCategoryService.updateCategory(id, req);
        return R.ok(true);
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping("/{id}/invalidate")
    public R<Boolean> invalidate(@PathVariable Long id) {
        supplierCategoryService.invalidate(id);
        return R.ok(true);
    }
}
