package com.dzgylxt.controller.purchase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dzgylxt.common.PageResult;
import com.dzgylxt.common.R;
import com.dzgylxt.controller.BaseController;
import com.dzgylxt.entity.purchase.Award;
import com.dzgylxt.entity.purchase.AwardItem;
import com.dzgylxt.service.IAwardService;
import com.dzgylxt.vo.purchase.AwardSaveReqVO;
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

import java.util.List;

/** 定标管理（设计 §5.3：/api/v1/awards）。 */
@RestController
@RequestMapping("/api/v1/awards")
public class AwardController extends BaseController<IAwardService, Award> {

    @Autowired
    private IAwardService awardService;

    @Override
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/page")
    public R<PageResult<Award>> page(@RequestParam(defaultValue = "1") long current,
                                     @RequestParam(defaultValue = "10") long size) {
        Page<Award> page = new Page<>(current, size);
        IPage<Award> result = service.page(page,
                new LambdaQueryWrapper<Award>().orderByDesc(Award::getId));
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), current, size));
    }

    /** 创建定标（仅已截标询价；按 SKU 可拆多供应商）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping
    public R<Long> create(@RequestBody AwardSaveReqVO req) {
        return R.ok(awardService.createAward(req));
    }

    /** 调整定标明细（REJECTED 后重提）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PutMapping("/{id}/items")
    public R<Boolean> updateItems(@PathVariable Long id, @RequestBody AwardSaveReqVO req) {
        awardService.updateAwardItems(id, req);
        return R.ok(true);
    }

    /** 提交审批（异常价人工复核提示）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping("/{id}/submit")
    public R<Long> submit(@PathVariable Long id) {
        return R.ok(awardService.submit(id));
    }

    /** 定标明细（含换算快照）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/{id}/items")
    public R<List<AwardItem>> items(@PathVariable Long id) {
        return R.ok(awardService.listItems(id));
    }
}
