package com.dzgylxt.mapper.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.order.OrderItem;
import org.apache.ibatis.annotations.Mapper;

/** 订单明细 Mapper。 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}
