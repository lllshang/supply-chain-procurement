package com.dzgylxt.controller.settlement;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dzgylxt.common.PageResult;
import com.dzgylxt.common.R;
import com.dzgylxt.entity.settlement.Settlement;
import com.dzgylxt.enums.SettlementStatus;
import com.dzgylxt.service.ISettlementService;
import com.dzgylxt.vo.settlement.SettlementDraftVO;
import com.dzgylxt.vo.settlement.SettlementSaveReqVO;
import com.dzgylxt.vo.settlement.PrepaymentCreateReqVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 结算管理（P3 设计 §5：/api/v1/settlements；独立控制器不走通用 save）。
 */
@RestController
@RequestMapping("/api/v1/settlements")
public class SettlementController {

    @Autowired
    private ISettlementService settlementService;

    /** 分页（订单/供应商/状态过滤）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/page")
    public R<PageResult<Settlement>> page(@RequestParam(defaultValue = "1") long current,
                                          @RequestParam(defaultValue = "10") long size,
                                          @RequestParam(required = false) Long orderId,
                                          @RequestParam(required = false) Long supplierId,
                                          @RequestParam(required = false) SettlementStatus status) {
        IPage<Settlement> result = settlementService.page(current, size, orderId, supplierId, status);
        // PB-01：回填结算维度派生付款进度（payStatus/paidProgress，不落库）
        settlementService.fillPayProgress(result.getRecords());
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), current, size));
    }

    /** 单据。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/{id}")
    public R<Settlement> getById(@PathVariable Long id) {
        Settlement settlement = settlementService.getById(id);
        if (settlement != null) {
            // PB-01：回填结算维度派生付款进度（payStatus/paidProgress，不落库）
            settlementService.fillPayProgress(java.util.List.of(settlement));
        }
        return R.ok(settlement);
    }

    /** 订单入口带出草稿。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/draft/from-order/{orderId}")
    public R<SettlementDraftVO> draftFromOrder(@PathVariable Long orderId) {
        return R.ok(settlementService.draftFromOrder(orderId));
    }

    /** 到货单入口带出草稿（优先）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/draft/from-arrival/{arrivalId}")
    public R<SettlementDraftVO> draftFromArrival(@PathVariable Long arrivalId) {
        return R.ok(settlementService.draftFromArrival(arrivalId));
    }

    /** R4：预付款结算草稿（订单发起，带出已付预付款）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/prepayment/draft/{orderId}")
    public R<SettlementDraftVO> draftPrepayment(@PathVariable Long orderId) {
        return R.ok(settlementService.draftPrepaymentFromOrder(orderId));
    }

    /** 创建结算单。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping
    public R<Long> create(@RequestBody SettlementSaveReqVO req) {
        return R.ok(settlementService.createSettlement(req));
    }

    /** R4：预付款结算（订单发起，无需到货/无结算数量）→ SETTLEMENT 审批 → 核销；尾款自动扣减。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping("/prepayment/{orderId}")
    public R<Long> createPrepayment(@PathVariable Long orderId,
                                    @RequestBody PrepaymentCreateReqVO req) {
        return R.ok(settlementService.createPrepaymentSettlement(orderId, req.getAmount(), req.getRemark()));
    }

    /** 修改重提（驳回留痕后）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody SettlementSaveReqVO req) {
        settlementService.updateSettlement(id, req);
        return R.ok(true);
    }

    /** 提交 SETTLEMENT 审批。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping("/{id}/submit")
    public R<Long> submit(@PathVariable Long id) {
        return R.ok(settlementService.submit(id));
    }
}
