package com.dzgylxt.service.impl.settlement;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.entity.settlement.Payment;
import com.dzgylxt.mapper.settlement.PaymentMapper;
import org.springframework.stereotype.Service;

/** 付款登记服务。 */
@Service
public class PaymentServiceImpl extends ServiceImpl<PaymentMapper, Payment> {
}
