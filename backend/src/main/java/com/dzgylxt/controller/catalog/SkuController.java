package com.dzgylxt.controller.catalog;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.catalog.Sku;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dzgylxt.controller.BaseController;

/** 商品 SKU 管理。 */
@RestController
@RequestMapping("/api/v1/catalog/skus")
public class SkuController extends BaseController<IService<Sku>, Sku> {
}
