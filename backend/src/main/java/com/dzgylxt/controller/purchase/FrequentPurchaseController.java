package com.dzgylxt.controller.purchase;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dzgylxt.common.PageResult;
import com.dzgylxt.common.R;
import com.dzgylxt.controller.BaseController;
import com.dzgylxt.entity.purchase.FrequentPurchase;
import com.dzgylxt.security.UserContext;
import com.dzgylxt.service.IFrequentPurchaseService;
import com.dzgylxt.vo.purchase.ApplyItemDraftVO;
import com.dzgylxt.vo.purchase.BringInReqVO;
import org.springframework.beans.factory.annotation.Autowired;
import com.dzgylxt.common.SecurityConstants;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 部门常购清单（设计 §5.1：/api/v1/frequent-purchases）。 */
@RestController
@RequestMapping("/api/v1/frequent-purchases")
public class FrequentPurchaseController extends BaseController<IFrequentPurchaseService, FrequentPurchase> {

    @Autowired
    private IFrequentPurchaseService frequentService;

    /** 部门隔离查询。 */
    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping
    public R<List<FrequentPurchase>> listByDept(@RequestParam(required = false) Long deptId) {
        return R.ok(frequentService.listByDept(deptId == null ? UserContext.getCurrentDeptId() : deptId));
    }

    /** 常购带入申请草稿（停用 SKU 拒绝）。 */
    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping("/bring-in")
    public R<List<ApplyItemDraftVO>> bringIn(@RequestBody BringInReqVO req) {
        Long deptId = req.getDeptId() == null ? UserContext.getCurrentDeptId() : req.getDeptId();
        return R.ok(frequentService.bringIn(deptId, req.getSkuIds()));
    }

    /** 分页（默认按 id 倒序）。 */
    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/page")
    public R<PageResult<FrequentPurchase>> page(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "10") long size) {
        Page<FrequentPurchase> result = service.page(new Page<>(current, size));
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), current, size));
    }
}
