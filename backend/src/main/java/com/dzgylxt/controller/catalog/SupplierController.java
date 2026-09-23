package com.dzgylxt.controller.catalog;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dzgylxt.common.PageResult;
import com.dzgylxt.common.R;
import com.dzgylxt.entity.catalog.Supplier;
import com.dzgylxt.enums.CoopStatus;
import com.dzgylxt.service.ISupplierService;
import com.dzgylxt.vo.supplier.SupplierAdmissionVO;
import com.dzgylxt.vo.supplier.SupplierPageReqVO;
import com.dzgylxt.vo.supplier.SupplierPageRespVO;
import com.dzgylxt.vo.supplier.SupplierSaveReqVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 供应商档案管理（含准入资格查询）。 */
@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

    private static final String GUARD = "isAuthenticated() and @authz.hasAnyPerm(authentication)";

    private final ISupplierService supplierService;

    public SupplierController(ISupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @PreAuthorize(GUARD)
    @GetMapping("/page")
    public R<PageResult<SupplierPageRespVO>> page(SupplierPageReqVO req) {
        IPage<SupplierPageRespVO> result = supplierService.pageSupplier(req);
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize()));
    }

    @PreAuthorize(GUARD)
    @GetMapping("/{id}")
    public R<Supplier> getById(@PathVariable Long id) {
        return R.ok(supplierService.getById(id));
    }

    @PreAuthorize(GUARD)
    @PostMapping
    public R<Long> create(@RequestBody SupplierSaveReqVO req) {
        return R.ok(supplierService.createSupplier(req));
    }

    @PreAuthorize(GUARD)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody SupplierSaveReqVO req) {
        supplierService.updateSupplier(id, req);
        return R.ok(true);
    }

    @PreAuthorize(GUARD)
    @PostMapping("/{id}/coop-status")
    public R<Boolean> updateCoopStatus(@PathVariable Long id,
                                       @RequestParam CoopStatus status) {
        // #33 name 契约：查询参数经 EnumWebConfig ConverterFactory 收 name/数值
        supplierService.updateCoopStatus(id, status);
        return R.ok(true);
    }

    @PreAuthorize(GUARD)
    @PostMapping("/{id}/blacklist")
    public R<Boolean> markBlacklist(@PathVariable Long id, @RequestParam boolean blacklist) {
        supplierService.markBlacklist(id, blacklist);
        return R.ok(true);
    }

    @PreAuthorize(GUARD)
    @GetMapping("/{id}/admission")
    public R<SupplierAdmissionVO> admission(@PathVariable Long id) {
        return R.ok(supplierService.getAdmission(id));
    }
}
