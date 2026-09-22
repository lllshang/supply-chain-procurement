package com.dzgylxt.controller.catalog;

import com.dzgylxt.common.R;
import com.dzgylxt.entity.catalog.ProductCategory;
import com.dzgylxt.service.IProductCategoryService;
import com.dzgylxt.vo.catalog.ProductCategorySaveReqVO;
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

/** 商品三级品类管理。 */
@RestController
@RequestMapping("/api/v1/catalog/categories")
public class ProductCategoryController {

    private static final String GUARD = "isAuthenticated() and @authz.hasAnyPerm(authentication)";

    private final IProductCategoryService categoryService;

    public ProductCategoryController(IProductCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PreAuthorize(GUARD)
    @GetMapping("/tree")
    public R<List<CategoryTreeNodeVO>> tree() {
        return R.ok(categoryService.tree());
    }

    @PreAuthorize(GUARD)
    @GetMapping("/list")
    public R<List<ProductCategory>> list() {
        return R.ok(categoryService.list());
    }

    @PreAuthorize(GUARD)
    @GetMapping("/{id}")
    public R<ProductCategory> getById(@PathVariable Long id) {
        return R.ok(categoryService.getById(id));
    }

    @PreAuthorize(GUARD)
    @PostMapping
    public R<Long> create(@RequestBody ProductCategorySaveReqVO req) {
        return R.ok(categoryService.createCategory(req));
    }

    @PreAuthorize(GUARD)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody ProductCategorySaveReqVO req) {
        categoryService.updateCategory(id, req);
        return R.ok(true);
    }

    @PreAuthorize(GUARD)
    @PostMapping("/{id}/invalidate")
    public R<Boolean> invalidate(@PathVariable Long id) {
        categoryService.invalidate(id);
        return R.ok(true);
    }
}
