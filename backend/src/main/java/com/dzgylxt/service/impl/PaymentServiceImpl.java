package com.dzgylxt.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.entity.Payment;
import com.dzgylxt.mapper.PaymentMapper;
import org.springframework.stereotype.Service;

/** 付款登记服务。 */
@Service
public class PaymentServiceImpl extends ServiceImpl<PaymentMapper, Payment> {
}
