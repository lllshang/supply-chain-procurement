package com.dzgylxt.controller;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.Quotation;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 报价管理。 */
@RestController
@RequestMapping("/api/v1/quotations")
public class QuotationController extends BaseController<IService<Quotation>, Quotation> {
}
