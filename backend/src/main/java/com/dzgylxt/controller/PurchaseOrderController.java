package com.dzgylxt.controller;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.PurchaseOrder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 采购订单管理。 */
@RestController
@RequestMapping("/api/v1/orders")
public class PurchaseOrderController extends BaseController<IService<PurchaseOrder>, PurchaseOrder> {
}
