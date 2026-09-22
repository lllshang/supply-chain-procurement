package com.dzgylxt.controller.approval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dzgylxt.approval.ApprovalDecision;
import com.dzgylxt.approval.ApprovalGateway;
import com.dzgylxt.common.PageResult;
import com.dzgylxt.common.R;
import com.dzgylxt.controller.BaseController;
import com.dzgylxt.entity.approval.ApprovalTask;
import com.dzgylxt.enums.ApprovalStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 审批中心统一入口（设计 §5.6：待办列表 + 同意/驳回；幂等由 Gateway 保证）。 */
@RestController
@RequestMapping("/api/v1/approvals/tasks")
public class ApprovalTaskController extends BaseController<com.dzgylxt.service.impl.approval.ApprovalTaskServiceImpl, ApprovalTask> {

    @Autowired
    private ApprovalGateway approvalGateway;

    /** 任务筛选列表（bizType/status 过滤；/page 沿用 BaseController 通用分页）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/search")
    public R<PageResult<ApprovalTask>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String bizType,
                                            @RequestParam(required = false) ApprovalStatus status) {
        LambdaQueryWrapper<ApprovalTask> wrapper = new LambdaQueryWrapper<>();
        if (bizType != null && !bizType.isBlank()) {
            wrapper.eq(ApprovalTask::getBizType, bizType);
        }
        if (status != null) {
            wrapper.eq(ApprovalTask::getStatus, status);
        }
        Page<ApprovalTask> page = new Page<>(current, size);
        IPage<ApprovalTask> result = service.page(page, wrapper.orderByDesc(ApprovalTask::getId));
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), current, size));
    }

    /** 同意（comment 可空）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping("/{taskId}/approve")
    public R<Boolean> approve(@PathVariable Long taskId, @RequestBody(required = false) CommentReq req) {
        approvalGateway.callback(taskId, ApprovalDecision.APPROVED,
                req == null ? null : req.getComment());
        return R.ok(true);
    }

    /** 驳回（comment 必填于驳回）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping("/{taskId}/reject")
    public R<Boolean> reject(@PathVariable Long taskId, @RequestBody(required = false) CommentReq req) {
        String comment = req == null ? null : req.getComment();
        if (comment == null || comment.isBlank()) {
            return R.fail(com.dzgylxt.common.ResultCode.PARAM_ERROR.getCode(), "驳回必须填写审批意见");
        }
        approvalGateway.callback(taskId, ApprovalDecision.REJECTED, comment);
        return R.ok(true);
    }

    /** 审批意见请求体。 */
    public static class CommentReq {
        private String comment;

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }
    }
}
