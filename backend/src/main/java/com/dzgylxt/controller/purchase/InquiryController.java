package com.dzgylxt.controller.purchase;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.purchase.Inquiry;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dzgylxt.controller.BaseController;

/** 询价单管理。 */
@RestController
@RequestMapping("/api/v1/inquiries")
public class InquiryController extends BaseController<IService<Inquiry>, Inquiry> {
}
