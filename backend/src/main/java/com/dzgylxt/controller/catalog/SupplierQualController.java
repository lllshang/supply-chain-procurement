package com.dzgylxt.controller.catalog;

import com.dzgylxt.common.R;
import com.dzgylxt.service.ISupplierQualService;
import com.dzgylxt.vo.supplier.SupplierQualRespVO;
import com.dzgylxt.vo.supplier.SupplierQualSaveReqVO;
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

/** 供应商资质管理（录入 / 审核闭环 / 重提）。 */
@RestController
@RequestMapping("/api/v1/suppliers/{supplierId}/quals")
public class SupplierQualController {

    private static final String GUARD = "isAuthenticated() and @authz.hasAnyPerm(authentication)";

    private final ISupplierQualService supplierQualService;

    public SupplierQualController(ISupplierQualService supplierQualService) {
        this.supplierQualService = supplierQualService;
    }

    @PreAuthorize(GUARD)
    @GetMapping
    public R<List<SupplierQualRespVO>> list(@PathVariable Long supplierId) {
        return R.ok(supplierQualService.listBySupplier(supplierId));
    }

    @PreAuthorize(GUARD)
    @PostMapping
    public R<Long> create(@PathVariable Long supplierId, @RequestBody SupplierQualSaveReqVO req) {
        req.setSupplierId(supplierId);
        return R.ok(supplierQualService.createQual(req));
    }

    @PreAuthorize(GUARD)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long supplierId, @PathVariable Long id,
                             @RequestBody SupplierQualSaveReqVO req) {
        req.setSupplierId(supplierId);
        supplierQualService.updateQual(id, req);
        return R.ok(true);
    }

    @PreAuthorize(GUARD)
    @PostMapping("/{id}/review")
    public R<Long> review(@PathVariable Long supplierId, @PathVariable Long id) {
        return R.ok(supplierQualService.submitForApproval(id));
    }

    @PreAuthorize(GUARD)
    @PostMapping("/{id}/resubmit")
    public R<Boolean> resubmit(@PathVariable Long supplierId, @PathVariable Long id,
                               @RequestBody SupplierQualSaveReqVO req) {
        req.setSupplierId(supplierId);
        supplierQualService.resubmit(id, req);
        return R.ok(true);
    }

    @PreAuthorize(GUARD)
    @PostMapping("/callback")
    public R<Boolean> callback(@PathVariable Long supplierId,
                               @RequestParam Long taskId,
                               @RequestParam boolean approved,
                               @RequestParam(required = false) String comment) {
        supplierQualService.onApprovalCallback(taskId, approved, comment);
        return R.ok(true);
    }
}
