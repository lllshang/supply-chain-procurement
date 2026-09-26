package com.dzgylxt.controller.catalog;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dzgylxt.common.PageResult;
import com.dzgylxt.common.R;
import com.dzgylxt.entity.catalog.Unit;
import com.dzgylxt.service.IUnitService;
import com.dzgylxt.vo.catalog.UnitSaveReqVO;
import com.dzgylxt.common.SecurityConstants;
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

/** 计量单位管理。 */
@RestController
@RequestMapping("/api/v1/catalog/units")
public class UnitController {


    private final IUnitService unitService;

    public UnitController(IUnitService unitService) {
        this.unitService = unitService;
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/page")
    public R<PageResult<Unit>> page(@RequestParam(defaultValue = "1") long current,
                                    @RequestParam(defaultValue = "10") long size) {
        IPage<Unit> result = unitService.page(new Page<>(current, size));
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), current, size));
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/list")
    public R<List<Unit>> list() {
        return R.ok(unitService.list());
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/{id}")
    public R<Unit> getById(@PathVariable Long id) {
        return R.ok(unitService.getById(id));
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping
    public R<Long> create(@RequestBody UnitSaveReqVO req) {
        return R.ok(unitService.createUnit(req));
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody UnitSaveReqVO req) {
        unitService.updateUnit(id, req);
        return R.ok(true);
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping("/{id}/invalidate")
    public R<Boolean> invalidate(@PathVariable Long id) {
        unitService.invalidate(id);
        return R.ok(true);
    }
}
