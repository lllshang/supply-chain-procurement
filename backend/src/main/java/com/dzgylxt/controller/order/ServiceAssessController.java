package com.dzgylxt.controller.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dzgylxt.common.PageResult;
import com.dzgylxt.common.R;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 服务验收考核（设计 §5.5：扣款供 P3 结算取数 D8；独立控制器）。 */
@RestController
@RequestMapping("/api/v1/service-assesses")
public class ServiceAssessController {

    @Autowired
    private IServiceAssessService assessService;

    /** 分页（id 倒序）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/page")
    public R<PageResult<ServiceAssess>> page(@RequestParam(defaultValue = "1") long current,
                                   @RequestParam(defaultValue = "10") long size) {
        Page<ServiceAssess> page = new Page<>(current, size);
        IPage<ServiceAssess> result = assessService.page(page,
                new LambdaQueryWrapper<ServiceAssess>().orderByDesc(ServiceAssess::getId));
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), current, size));
    }

    /** 单据详情。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/{id}")
    public R<ServiceAssess> getById(@PathVariable Long id) {
        return R.ok(assessService.getById(id));
    }

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
