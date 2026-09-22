package com.dzgylxt.controller.catalog;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.catalog.Spu;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dzgylxt.controller.BaseController;

/** 商品 SPU 管理。 */
@RestController
@RequestMapping("/api/v1/catalog/spus")
public class SpuController extends BaseController<IService<Spu>, Spu> {
}
