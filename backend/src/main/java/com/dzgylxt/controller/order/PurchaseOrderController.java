package com.dzgylxt.controller.order;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.order.PurchaseOrder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dzgylxt.controller.BaseController;

/** 采购订单管理。 */
@RestController
@RequestMapping("/api/v1/orders")
public class PurchaseOrderController extends BaseController<IService<PurchaseOrder>, PurchaseOrder> {
}
