package com.dzgylxt.controller.order;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dzgylxt.common.R;
import com.dzgylxt.controller.BaseController;
import com.dzgylxt.entity.order.Arrival;
import com.dzgylxt.entity.order.ArrivalItem;
import com.dzgylxt.enums.HandleType;
import com.dzgylxt.service.IArrivalService;
import com.dzgylxt.vo.order.ArrivalCreateReqVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** 到货验收管理（设计 §5.5：/api/v1/arrivals）。 */
@RestController
@RequestMapping("/api/v1/arrivals")
public class ArrivalController extends BaseController<IArrivalService, Arrival> {

    @Autowired
    private IArrivalService arrivalService;

    /** 到货登记（按订单展开明细，应收=未入库余量）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping
    public R<Long> create(@RequestBody ArrivalCreateReqVO req) {
        return R.ok(arrivalService.createArrival(req));
    }

    /** 到货明细。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/{id}/items")
    public R<List<ArrivalItem>> items(@PathVariable Long id) {
        return R.ok(arrivalService.listItems(id));
    }

    /** 入库确认（qty_stored 分次累加，基本单位）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping("/items/{id}/store")
    public R<Boolean> store(@PathVariable Long id, @RequestBody StoreReq req) {
        arrivalService.confirmStore(id, req.getQtyStored());
        return R.ok(true);
    }

    /** 差异处理（接受/退货/补货；退货补货联动履约调整草稿）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping("/items/{id}/handle")
    public R<Boolean> handle(@PathVariable Long id, @RequestBody HandleReq req) {
        arrivalService.handleDiff(id, req.getType());
        return R.ok(true);
    }

    /** 入库台账流水（按订单/供应商/日期筛选；Q8 只记台账）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/ledger")
    public R<IPage<ArrivalItem>> ledger(@RequestParam(defaultValue = "1") long current,
                                        @RequestParam(defaultValue = "10") long size,
                                        @RequestParam(required = false) Long orderId,
                                        @RequestParam(required = false) Long supplierId,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(arrivalService.ledger(current, size, orderId, supplierId, from, to));
    }

    /** 入库请求体。 */
    public static class StoreReq {
        private java.math.BigDecimal qtyStored;

        public java.math.BigDecimal getQtyStored() {
            return qtyStored;
        }

        public void setQtyStored(java.math.BigDecimal qtyStored) {
            this.qtyStored = qtyStored;
        }
    }

    /** 差异处理请求体。 */
    public static class HandleReq {
        private HandleType type;

        public HandleType getType() {
            return type;
        }

        public void setType(HandleType type) {
            this.type = type;
        }
    }
}
