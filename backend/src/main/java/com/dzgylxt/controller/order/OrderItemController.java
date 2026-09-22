package com.dzgylxt.controller.order;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.order.OrderItem;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dzgylxt.controller.BaseController;

/** 订单明细管理。 */
@RestController
@RequestMapping("/api/v1/orders/items")
public class OrderItemController extends BaseController<IService<OrderItem>, OrderItem> {
}
