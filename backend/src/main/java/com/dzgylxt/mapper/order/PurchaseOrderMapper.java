package com.dzgylxt.mapper.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.order.PurchaseOrder;
import org.apache.ibatis.annotations.Mapper;

/** 采购订单 Mapper。 */
@Mapper
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrder> {
}
