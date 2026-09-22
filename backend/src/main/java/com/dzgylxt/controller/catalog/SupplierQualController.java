package com.dzgylxt.controller.catalog;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.catalog.SupplierQual;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dzgylxt.controller.BaseController;

/** 供应商资质管理。 */
@RestController
@RequestMapping("/api/v1/suppliers/quals")
public class SupplierQualController extends BaseController<IService<SupplierQual>, SupplierQual> {
}
