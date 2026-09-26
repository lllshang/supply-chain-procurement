package com.dzgylxt.controller.catalog;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dzgylxt.common.PageResult;
import com.dzgylxt.common.R;
import com.dzgylxt.entity.catalog.Sku;
import com.dzgylxt.service.ISkuService;
import com.dzgylxt.vo.catalog.SkuPageReqVO;
import com.dzgylxt.vo.catalog.SkuPageRespVO;
import com.dzgylxt.vo.catalog.SkuSaveReqVO;
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

/** 商品 SKU 管理。 */
@RestController
@RequestMapping("/api/v1/catalog/skus")
public class SkuController {


    private final ISkuService skuService;

    public SkuController(ISkuService skuService) {
        this.skuService = skuService;
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/page")
    public R<PageResult<SkuPageRespVO>> page(SkuPageReqVO req) {
        IPage<SkuPageRespVO> result = skuService.pageSku(req);
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize()));
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/{id}")
    public R<Sku> getById(@PathVariable Long id) {
        return R.ok(skuService.getById(id));
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/spu/{spuId}")
    public R<List<Sku>> listBySpu(@PathVariable Long spuId) {
        return R.ok(skuService.listBySpu(spuId));
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping
    public R<Long> create(@RequestBody SkuSaveReqVO req) {
        return R.ok(skuService.createSku(req));
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody SkuSaveReqVO req) {
        skuService.updateSku(id, req);
        return R.ok(true);
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping("/{id}/enable")
    public R<Boolean> enable(@PathVariable Long id) {
        skuService.enable(id);
        return R.ok(true);
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping("/{id}/disable")
    public R<Boolean> disable(@PathVariable Long id) {
        skuService.disable(id);
        return R.ok(true);
    }
}
