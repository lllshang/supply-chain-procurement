package com.dzgylxt.service.impl.order;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.entity.order.OrderItem;
import com.dzgylxt.mapper.order.OrderItemMapper;
import org.springframework.stereotype.Service;

/** 订单明细服务。 */
@Service
public class OrderItemServiceImpl extends ServiceImpl<OrderItemMapper, OrderItem> {
}
