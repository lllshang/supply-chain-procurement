package com.dzgylxt.controller.catalog;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.catalog.Supplier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dzgylxt.controller.BaseController;

/** 供应商管理。 */
@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController extends BaseController<IService<Supplier>, Supplier> {
}
