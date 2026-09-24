package com.dzgylxt.controller.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dzgylxt.common.PageResult;
import com.dzgylxt.common.R;
import com.dzgylxt.entity.order.OrderChange;
import com.dzgylxt.entity.order.OrderItem;
import com.dzgylxt.entity.order.PurchaseOrder;
import com.dzgylxt.mapper.order.OrderChangeMapper;
import com.dzgylxt.service.IOrderService;
import com.dzgylxt.vo.order.OrderChangeReqVO;
import com.dzgylxt.vo.order.OrderCreateReqVO;
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

/** 采购订单管理（设计 §5.5：/api/v1/orders，三重校验事务入口；独立控制器）。 */
@RestController
@RequestMapping("/api/v1/orders")
public class PurchaseOrderController {

    @Autowired
    private IOrderService orderService;

    @Autowired
    private OrderChangeMapper orderChangeMapper;

    /** 分页（id 倒序），附带 R5 派生进度（结清/付清）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/page")
    public R<PageResult<PurchaseOrder>> page(@RequestParam(defaultValue = "1") long current,
                                   @RequestParam(defaultValue = "10") long size) {
        Page<PurchaseOrder> page = new Page<>(current, size);
        IPage<PurchaseOrder> result = orderService.page(page,
                new LambdaQueryWrapper<PurchaseOrder>().orderByDesc(PurchaseOrder::getId));
        orderService.fillProgress(result.getRecords());
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), current, size));
    }

    /** 单据详情，附带 R5 派生进度（结清/付清）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/{id}")
    public R<PurchaseOrder> getById(@PathVariable Long id) {
        PurchaseOrder order = orderService.getById(id);
        if (order != null) {
            orderService.fillProgress(java.util.List.of(order));
        }
        return R.ok(order);
    }

    /** 下单（三重校验事务；物料/服务拆单，返回订单 id 列表）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping
    public R<List<Long>> create(@RequestBody OrderCreateReqVO req) {
        return R.ok(orderService.createOrder(req));
    }

    /** 订单明细（含换算快照与来源追溯）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/{id}/items")
    public R<List<OrderItem>> items(@PathVariable Long id) {
        return R.ok(orderService.listItems(id));
    }

    /** 全链路追溯（申请/询价/定标/合同/订单/到货/变更）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/{id}/trace")
    public R<Object> trace(@PathVariable Long id) {
        return R.ok(orderService.trace(id));
    }

    /** 订单变更（仅 CREATED，重跑三重校验，免审留痕 Q5/Q9）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping("/{id}/change")
    public R<Boolean> change(@PathVariable Long id, @RequestBody OrderChangeReqVO req) {
        orderService.changeOrder(id, req);
        return R.ok(true);
    }

    /** 取消（释放合同额度 + 回冲申请余量）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping("/{id}/cancel")
    public R<Boolean> cancel(@PathVariable Long id, @RequestBody CancelReq req) {
        orderService.cancelOrder(id, req.getReason());
        return R.ok(true);
    }

    /** 变更留痕列表。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/{id}/changes")
    public R<List<OrderChange>> changes(@PathVariable Long id) {
        return R.ok(orderChangeMapper.selectList(
                new LambdaQueryWrapper<OrderChange>().eq(OrderChange::getOrderId, id)));
    }

    /** 取消请求体。 */
    public static class CancelReq {
        private String reason;

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
