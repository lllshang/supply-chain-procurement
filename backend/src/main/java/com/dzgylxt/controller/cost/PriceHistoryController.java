package com.dzgylxt.controller.cost;

import com.dzgylxt.common.R;
import com.dzgylxt.entity.cost.PriceHistory;
import com.dzgylxt.service.IPriceHistoryService;
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

/**
 * 价格库管理（P3 设计 T07：/api/v1/price-histories）。
 *
 * <p>比价历史取数已切 price_history（空库兜底回实时计算，见 InquiryServiceImpl）；
 * 本控制器提供待审列表与审核动作（异常价人工复核闭环）。</p>
 */
@RestController
@RequestMapping("/api/v1/price-histories")
public class PriceHistoryController {

    @Autowired
    private IPriceHistoryService priceHistoryService;

    /** 待审价列表（异常价人工复核队列）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/pending")
    public R<List<PriceHistory>> pending() {
        return R.ok(priceHistoryService.pendingList());
    }

    /** 某 SKU 近期通过价（比价历史取数口径复现，供前端展示）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/recent")
    public R<List<PriceHistory>> recent(@RequestParam Long skuId,
                                        @RequestParam(defaultValue = "5") int limit) {
        return R.ok(priceHistoryService.recent(skuId, limit));
    }

    /** 审核待审价（通过/驳回；仅 PENDING 可流转）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping("/{id}/audit")
    public R<Boolean> audit(@PathVariable Long id, @RequestBody AuditReq req) {
        priceHistoryService.audit(id, req.isApproved(), req.getRemark());
        return R.ok(true);
    }

    /** 审核请求体。 */
    public static class AuditReq {
        private boolean approved;
        private String remark;

        public boolean isApproved() {
            return approved;
        }

        public void setApproved(boolean approved) {
            this.approved = approved;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }
}
