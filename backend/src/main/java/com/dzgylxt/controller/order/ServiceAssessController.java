package com.dzgylxt.controller.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dzgylxt.common.R;
import com.dzgylxt.controller.BaseController;
import com.dzgylxt.entity.order.ServiceAssess;
import com.dzgylxt.service.IServiceAssessService;
import com.dzgylxt.vo.order.ServiceAssessSaveReqVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 服务验收考核（设计 §5.5：/api/v1/orders/{orderId}/service-assess 口径；扣款供 P3 结算 D8）。 */
@RestController
@RequestMapping("/api/v1/service-assesses")
public class ServiceAssessController extends BaseController<IServiceAssessService, ServiceAssess> {

    @Autowired
    private IServiceAssessService assessService;

    /** 登记考核（仅服务订单 order_type=1）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping
    public R<Long> assess(@RequestBody ServiceAssessSaveReqVO req) {
        return R.ok(assessService.assess(req));
    }

    /** 按订单查考核记录。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/order/{orderId}")
    public R<List<ServiceAssess>> byOrder(@PathVariable Long orderId) {
        return R.ok(assessService.list(
                new LambdaQueryWrapper<ServiceAssess>().eq(ServiceAssess::getOrderId, orderId)));
    }
}
