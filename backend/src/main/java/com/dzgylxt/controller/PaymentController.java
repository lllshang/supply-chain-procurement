package com.dzgylxt.controller;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.Payment;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 付款登记管理。 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController extends BaseController<IService<Payment>, Payment> {
}
