package com.dzgylxt.controller.order;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dzgylxt.common.R;
import com.dzgylxt.controller.BaseController;
import com.dzgylxt.entity.order.FulfillmentAdjust;
import com.dzgylxt.enums.AdjustType;
import com.dzgylxt.service.IFulfillmentAdjustService;
import com.dzgylxt.vo.order.AdjustSaveReqVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 履约调整台账（设计 §5.5：/api/v1/fulfillment-adjusts；阈值内免审）。 */
@RestController
@RequestMapping("/api/v1/fulfillment-adjusts")
public class FulfillmentAdjustController extends BaseController<IFulfillmentAdjustService, FulfillmentAdjust> {

    @Autowired
    private IFulfillmentAdjustService adjustService;

    /** 创建调整单（草稿）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping
    public R<Long> create(@RequestBody AdjustSaveReqVO req) {
        return R.ok(adjustService.createAdjust(req));
    }

    /** 提交（阈值内免审生效；超阈值走 FULFILLMENT_ADJUST 审批）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping("/{id}/submit")
    public R<Long> submit(@PathVariable Long id) {
        return R.ok(adjustService.submit(id));
    }

    /** 台账筛选（类型/订单）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/page")
    public R<IPage<FulfillmentAdjust>> pageAdjust(@RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "10") long size,
                                                  @RequestParam(required = false) Long orderId,
                                                  @RequestParam(required = false) AdjustType adjustType) {
        return R.ok(adjustService.pageAdjust(current, size, orderId, adjustType));
    }
}
