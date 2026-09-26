package com.dzgylxt.controller.catalog;

import com.dzgylxt.common.R;
import com.dzgylxt.entity.catalog.SpecOption;
import com.dzgylxt.service.ISpecOptionService;
import com.dzgylxt.vo.catalog.SpecOptionSaveReqVO;
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
import java.util.Map;

/** 规格配置管理。 */
@RestController
@RequestMapping("/api/v1/catalog/spec-options")
public class SpecOptionController {


    private final ISpecOptionService specOptionService;

    public SpecOptionController(ISpecOptionService specOptionService) {
        this.specOptionService = specOptionService;
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/list")
    public R<List<SpecOption>> list() {
        return R.ok(specOptionService.list());
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/grouped")
    public R<Map<String, List<String>>> grouped() {
        return R.ok(specOptionService.groupedOptions());
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/{id}")
    public R<SpecOption> getById(@PathVariable Long id) {
        return R.ok(specOptionService.getById(id));
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping
    public R<Long> create(@RequestBody SpecOptionSaveReqVO req) {
        return R.ok(specOptionService.createSpecOption(req));
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody SpecOptionSaveReqVO req) {
        specOptionService.updateSpecOption(id, req);
        return R.ok(true);
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping("/{id}/invalidate")
    public R<Boolean> invalidate(@PathVariable Long id) {
        specOptionService.invalidate(id);
        return R.ok(true);
    }
}
