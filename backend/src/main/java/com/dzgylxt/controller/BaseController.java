package com.dzgylxt.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.common.BaseEntity;
import com.dzgylxt.common.PageResult;
import com.dzgylxt.common.R;
import org.springframework.beans.factory.annotation.Autowired;
import com.dzgylxt.common.SecurityConstants;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 通用 CRUD 控制器骨架（阶段一不写复杂业务，仅模板 + 分页）。
 *
 * <p>所有管理端点统一要求「已登录且至少拥有一个权限」：未登录返回 401，
 * 0 角色 / 0 权限用户返回 403（详见 {@code AuthzService}）。前端按钮隐藏仅作 UX，
 * 此处为真实安全边界。阶段二应细化到 {@code @PreAuthorize("hasAuthority('module:action')")}。</p>
 *
 * @param <S> 业务 Service（MyBatis-Plus IService）
 * @param <T> 实体类型
 */
public abstract class BaseController<S extends IService<T>, T extends BaseEntity> {

    @Autowired
    protected S service;

    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/page")
    public R<PageResult<T>> page(@RequestParam(defaultValue = "1") long current,
                                 @RequestParam(defaultValue = "10") long size) {
        Page<T> page = new Page<>(current, size);
        IPage<T> result = service.page(page);
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), current, size));
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/list")
    public R<List<T>> list() {
        return R.ok(service.list());
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/{id}")
    public R<T> getById(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping
    public R<Boolean> save(@RequestBody T entity) {
        return R.ok(service.save(entity));
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody T entity) {
        entity.setId(id);
        return R.ok(service.updateById(entity));
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @DeleteMapping("/{id}")
    public R<Boolean> remove(@PathVariable Long id) {
        return R.ok(service.removeById(id));
    }
}
