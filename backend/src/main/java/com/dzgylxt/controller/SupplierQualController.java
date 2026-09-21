package com.dzgylxt.controller;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.SupplierQual;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 供应商资质管理。 */
@RestController
@RequestMapping("/api/v1/suppliers/quals")
public class SupplierQualController extends BaseController<IService<SupplierQual>, SupplierQual> {
}
