package com.dzgylxt.controller.purchase;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.purchase.Quotation;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dzgylxt.controller.BaseController;

/** 报价管理。 */
@RestController
@RequestMapping("/api/v1/quotations")
public class QuotationController extends BaseController<IService<Quotation>, Quotation> {
}
