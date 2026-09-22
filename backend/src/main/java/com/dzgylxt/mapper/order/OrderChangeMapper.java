package com.dzgylxt.mapper.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.order.OrderChange;
import org.apache.ibatis.annotations.Mapper;

/** 订单变更留痕 Mapper。 */
@Mapper
public interface OrderChangeMapper extends BaseMapper<OrderChange> {
}
