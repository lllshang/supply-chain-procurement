package com.dzgylxt.mapper.settlement;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.settlement.Payment;
import org.apache.ibatis.annotations.Mapper;

/** 付款登记 Mapper。 */
@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {
}
