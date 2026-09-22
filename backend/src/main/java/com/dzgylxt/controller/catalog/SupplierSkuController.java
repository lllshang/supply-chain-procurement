package com.dzgylxt.controller.catalog;

import com.dzgylxt.common.R;
import com.dzgylxt.service.ISupplierSkuService;
import com.dzgylxt.vo.supplier.BatchBindRespVO;
import com.dzgylxt.vo.supplier.SupplierSkuRespVO;
import com.dzgylxt.vo.supplier.SupplierSkuSaveReqVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 供应商-商品绑定管理。 */
@RestController
@RequestMapping("/api/v1/supplier-skus")
public class SupplierSkuController {

    private static final String GUARD = "isAuthenticated() and @authz.hasAnyPerm(authentication)";

    private final ISupplierSkuService supplierSkuService;

    public SupplierSkuController(ISupplierSkuService supplierSkuService) {
        this.supplierSkuService = supplierSkuService;
    }

    @PreAuthorize(GUARD)
    @GetMapping
    public R<List<SupplierSkuRespVO>> list(@RequestParam Long supplierId) {
        return R.ok(supplierSkuService.listBySupplier(supplierId));
    }

    @PreAuthorize(GUARD)
    @PostMapping
    public R<Long> bind(@RequestBody SupplierSkuSaveReqVO req) {
        return R.ok(supplierSkuService.bind(req));
    }

    @PreAuthorize(GUARD)
    @DeleteMapping("/{id}")
    public R<Boolean> unbind(@PathVariable Long id) {
        supplierSkuService.unbind(id);
        return R.ok(true);
    }

    @PreAuthorize(GUARD)
    @PostMapping("/batch")
    public R<BatchBindRespVO> batchBind(@RequestParam("file") MultipartFile file) {
        return R.ok(supplierSkuService.batchBind(file));
    }
}
