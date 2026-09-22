package com.dzgylxt.controller.purchase;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.purchase.PurchaseApplyItem;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dzgylxt.controller.BaseController;

/** 采购申请明细管理。 */
@RestController
@RequestMapping("/api/v1/purchase-requests/items")
public class PurchaseApplyItemController extends BaseController<IService<PurchaseApplyItem>, PurchaseApplyItem> {
}
