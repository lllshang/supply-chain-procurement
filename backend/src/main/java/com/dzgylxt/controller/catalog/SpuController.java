package com.dzgylxt.controller.catalog;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dzgylxt.common.PageResult;
import com.dzgylxt.common.R;
import com.dzgylxt.entity.catalog.Sku;
import com.dzgylxt.entity.catalog.Spu;
import com.dzgylxt.service.IProductImportService;
import com.dzgylxt.service.ISkuService;
import com.dzgylxt.service.ISpuService;
import com.dzgylxt.vo.catalog.ProductExportReqVO;
import com.dzgylxt.vo.catalog.ProductImportPreviewVO;
import com.dzgylxt.vo.catalog.SpuPageReqVO;
import com.dzgylxt.vo.catalog.SpuPageRespVO;
import com.dzgylxt.vo.catalog.SpuSaveReqVO;
import com.dzgylxt.vo.common.ImportTaskVO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 商品 SPU 管理（含导入/导出）。 */
@RestController
@RequestMapping("/api/v1/catalog/spus")
public class SpuController {

    private static final String GUARD = "isAuthenticated() and @authz.hasAnyPerm(authentication)";

    private final ISpuService spuService;
    private final ISkuService skuService;
    private final IProductImportService productImportService;

    public SpuController(ISpuService spuService,
                         ISkuService skuService,
                         IProductImportService productImportService) {
        this.spuService = spuService;
        this.skuService = skuService;
        this.productImportService = productImportService;
    }

    @PreAuthorize(GUARD)
    @GetMapping("/page")
    public R<PageResult<SpuPageRespVO>> page(SpuPageReqVO req) {
        IPage<SpuPageRespVO> result = spuService.pageSpu(req);
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize()));
    }

    @PreAuthorize(GUARD)
    @GetMapping("/{id}")
    public R<Spu> getById(@PathVariable Long id) {
        return R.ok(spuService.getById(id));
    }

    @PreAuthorize(GUARD)
    @GetMapping("/{spuId}/skus")
    public R<List<Sku>> listSkus(@PathVariable Long spuId) {
        return R.ok(skuService.listBySpu(spuId));
    }

    @PreAuthorize(GUARD)
    @PostMapping
    public R<Long> create(@RequestBody SpuSaveReqVO req) {
        return R.ok(spuService.createSpu(req));
    }

    @PreAuthorize(GUARD)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody SpuSaveReqVO req) {
        spuService.updateSpu(id, req);
        return R.ok(true);
    }

    @PreAuthorize(GUARD)
    @PostMapping("/{id}/enable")
    public R<Boolean> enable(@PathVariable Long id) {
        spuService.enable(id);
        return R.ok(true);
    }

    @PreAuthorize(GUARD)
    @PostMapping("/{id}/disable")
    public R<Boolean> disable(@PathVariable Long id) {
        spuService.disable(id);
        return R.ok(true);
    }

    @PreAuthorize(GUARD)
    @PostMapping("/import/single")
    public R<ImportTaskVO> importSingle(@RequestParam("file") MultipartFile file) {
        return R.ok(productImportService.importSingle(file));
    }

    @PreAuthorize(GUARD)
    @PostMapping("/import/multi/preview")
    public R<ProductImportPreviewVO> previewMulti(@RequestParam("file") MultipartFile file) {
        return R.ok(productImportService.previewMulti(file));
    }

    @PreAuthorize(GUARD)
    @PostMapping("/import/multi")
    public R<ImportTaskVO> importMulti(@RequestParam("file") MultipartFile file) {
        return R.ok(productImportService.importMulti(file));
    }

    @PreAuthorize(GUARD)
    @PostMapping("/export")
    public R<ImportTaskVO> export(@RequestBody ProductExportReqVO req) {
        return R.ok(productImportService.export(req, "v1"));
    }

    @PreAuthorize(GUARD)
    @GetMapping("/export/{taskId}")
    public R<ImportTaskVO> exportStatus(@PathVariable String taskId) {
        return R.ok(productImportService.exportTaskStatus(taskId));
    }

    @PreAuthorize(GUARD)
    @GetMapping("/export/{taskId}/download")
    public ResponseEntity<byte[]> download(@PathVariable String taskId) {
        byte[] bytes = productImportService.exportBytes(taskId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"product-export.xlsx\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }
}
