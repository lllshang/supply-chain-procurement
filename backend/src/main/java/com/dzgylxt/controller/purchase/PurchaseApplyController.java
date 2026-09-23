package com.dzgylxt.controller.purchase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dzgylxt.common.PageResult;
import com.dzgylxt.common.R;
import com.dzgylxt.entity.purchase.PurchaseApply;
import com.dzgylxt.service.IPurchaseApplyService;
import com.dzgylxt.vo.purchase.ApplyDetailRespVO;
import com.dzgylxt.vo.purchase.ApplySaveReqVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * 采购申请管理（设计 §5.1：/api/v1/purchase-requests）。
 *
 * <p>POST/PUT 接收 {@link ApplySaveReqVO}；兼容 P1 前端仅建头的 {@code {title, type}}
 * 表单（items 可空，提交前必须补明细）。独立控制器（不走 BaseController 通用 save，
 * 避免实体直落与 VO 创建的映射冲突）。</p>
 */
@RestController
@RequestMapping("/api/v1/purchase-requests")
public class PurchaseApplyController {

    @Autowired
    private IPurchaseApplyService applyService;

    /** 分页（id 倒序）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/page")
    public R<PageResult<PurchaseApply>> page(@RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "10") long size) {
        Page<PurchaseApply> page = new Page<>(current, size);
        IPage<PurchaseApply> result = applyService.page(page,
                new LambdaQueryWrapper<PurchaseApply>().orderByDesc(PurchaseApply::getId));
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), current, size));
    }

    /** 单头。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/{id}")
    public R<PurchaseApply> getById(@PathVariable Long id) {
        return R.ok(applyService.getById(id));
    }

    /** 详情（头 + 明细含换算快照列）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/{id}/items")
    public R<ApplyDetailRespVO> detail(@PathVariable Long id) {
        return R.ok(applyService.detail(id));
    }

    /** 创建申请（明细可空，兼容 P1 表单）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping
    public R<Long> save(@RequestBody ApplySaveReqVO req) {
        return R.ok(applyService.createApply(req));
    }

    /** 编辑申请（仅 DRAFT/REJECTED）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody ApplySaveReqVO req) {
        applyService.updateApply(id, req);
        return R.ok(true);
    }

    /** 提交（预算软校验 → 两级审批）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping("/{id}/submit")
    public R<Long> submit(@PathVariable Long id) {
        return R.ok(applyService.submit(id));
    }

    /** 导出申请单（OOXML，单头+明细两 Sheet；与其他导出格式统一，QA #26）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id) {
        byte[] bytes = applyService.exportApply(id);
        String filename = "purchase-apply-" + id + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    /** 作废申请（P3 设计 §2 行10；未转单完成前可作废并释放预算占用）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping("/{id}/close")
    public R<Boolean> close(@PathVariable Long id, @RequestBody(required = false) CloseReq req) {
        applyService.closeApply(id, req == null ? null : req.getReason());
        return R.ok(true);
    }

    /** 作废请求体。 */
    public static class CloseReq {
        private String reason;

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
