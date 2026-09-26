package com.dzgylxt.controller.catalog;

import com.dzgylxt.common.R;
import com.dzgylxt.entity.catalog.UnitConversion;
import com.dzgylxt.service.IUnitConversionService;
import com.dzgylxt.vo.catalog.UnitConversionSaveReqVO;
import com.dzgylxt.common.SecurityConstants;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 单位换算管理。 */
@RestController
@RequestMapping("/api/v1/catalog/unit-conversions")
public class UnitConversionController {


    private final IUnitConversionService unitConversionService;

    public UnitConversionController(IUnitConversionService unitConversionService) {
        this.unitConversionService = unitConversionService;
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping
    public R<List<UnitConversion>> history(@RequestParam Long skuId) {
        return R.ok(unitConversionService.history(skuId));
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/current")
    public R<UnitConversion> current(@RequestParam Long skuId, @RequestParam String fromUnit) {
        return R.ok(unitConversionService.currentEffective(skuId, fromUnit));
    }

    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping
    public R<Long> save(@RequestBody UnitConversionSaveReqVO req) {
        return R.ok(unitConversionService.saveConversion(req));
    }
}
