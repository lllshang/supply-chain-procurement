package com.dzgylxt.controller.catalog;

import com.dzgylxt.common.R;
import com.dzgylxt.entity.catalog.ProductCategory;
import com.dzgylxt.service.IProductCategoryService;
import com.dzgylxt.vo.catalog.ProductCategorySaveReqVO;
import com.dzgylxt.vo.common.CategoryTreeNodeVO;
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

/** 商品三级品类管理。 */
@RestController
@RequestMapping("/api/v1/catalog/categories")
public class ProductCategoryController {


    private final IProductCategoryService categoryService;

    public ProductCategoryController(IProductCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/tree")
    public R<List<CategoryTreeNodeVO>> tree() {
        return R.ok(categoryService.tree());
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/list")
    public R<List<ProductCategory>> list() {
        return R.ok(categoryService.list());
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/{id}")
    public R<ProductCategory> getById(@PathVariable Long id) {
        return R.ok(categoryService.getById(id));
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping
    public R<Long> create(@RequestBody ProductCategorySaveReqVO req) {
        return R.ok(categoryService.createCategory(req));
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody ProductCategorySaveReqVO req) {
        categoryService.updateCategory(id, req);
        return R.ok(true);
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping("/{id}/invalidate")
    public R<Boolean> invalidate(@PathVariable Long id) {
        categoryService.invalidate(id);
        return R.ok(true);
    }
}
