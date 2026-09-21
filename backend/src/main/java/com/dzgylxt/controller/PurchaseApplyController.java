package com.dzgylxt.controller;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.PurchaseApply;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 采购申请管理。 */
@RestController
@RequestMapping("/api/v1/purchase-requests")
public class PurchaseApplyController extends BaseController<IService<PurchaseApply>, PurchaseApply> {
}
