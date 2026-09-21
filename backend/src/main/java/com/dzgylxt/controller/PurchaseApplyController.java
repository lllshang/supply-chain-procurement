package com.dzgylxt.controller;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.common.R;
import com.dzgylxt.entity.PurchaseApply;
import com.dzgylxt.enums.PurchaseApplyStatus;
import com.dzgylxt.security.UserContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 采购申请管理。 */
@RestController
@RequestMapping("/api/v1/purchase-requests")
public class PurchaseApplyController extends BaseController<IService<PurchaseApply>, PurchaseApply> {

    @Override
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping
    public R<Boolean> save(@RequestBody PurchaseApply entity) {
        // 后端强制填充安全/审计字段，禁止前端直传落库（修复 P1-5：dept_id NOT NULL）
        if (entity.getDeptId() == null) {
            entity.setDeptId(UserContext.getCurrentDeptId());
        }
        if (entity.getApplicantId() == null) {
            entity.setApplicantId(UserContext.getCurrentUserId());
        }
        if (entity.getApplyNo() == null || entity.getApplyNo().isBlank()) {
            entity.setApplyNo("PA" + System.currentTimeMillis());
        }
        if (entity.getStatus() == null) {
            entity.setStatus(PurchaseApplyStatus.DRAFT);
        }
        return super.save(entity);
    }
}
