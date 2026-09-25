package com.dzgylxt.controller.approval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dzgylxt.approval.ApprovalDecision;
import com.dzgylxt.approval.ApprovalGateway;
import com.dzgylxt.common.PageResult;
import com.dzgylxt.common.R;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.controller.BaseController;
import com.dzgylxt.entity.approval.ApprovalTask;
import com.dzgylxt.enums.ApprovalStatus;
import com.dzgylxt.security.LoginUser;
import com.dzgylxt.security.UserContext;
import com.dzgylxt.service.impl.approval.ApprovalTaskServiceImpl;
import com.dzgylxt.vo.approval.ApprovalDoneVO;
import com.dzgylxt.vo.approval.ApprovalTaskDetailVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 审批中心工作台统一入口（P4 设计 §6.1）。
 *
 * <p>权限收紧（P3 收尾惯例延续）：具名 {@code hasPerm} 精确绑定，退役裸 {@code hasAnyPerm}
 * （仅遗留 /search 兼容一版）。todo 服务端按当前节点候选人过滤；approve/reject 网关内
 * 二次校验候选人（非候选 2004 不留痕），驳回意见前后端双拦截；并发冲突 3002。</p>
 */
@RestController
@RequestMapping("/api/v1/approvals/tasks")
public class ApprovalTaskController extends BaseController<ApprovalTaskServiceImpl, ApprovalTask> {

    private static final String PERM_TODO = "isAuthenticated() and @authz.hasPerm(authentication,'approval:todo')";
    private static final String PERM_DONE = "isAuthenticated() and @authz.hasPerm(authentication,'approval:done')";
    private static final String PERM_APPROVE = "isAuthenticated() and @authz.hasPerm(authentication,'approval:approve')";

    @Autowired
    private ApprovalGateway approvalGateway;

    /** 待办分页（bizType 筛选；服务端按当前节点候选人过滤，规格 §3.2）。 */
    @PreAuthorize(PERM_TODO)
    @GetMapping("/todo")
    public R<PageResult<ApprovalTask>> todo(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String bizType) {
        LoginUser user = UserContext.get();
        if (user == null) {
            return R.fail(ResultCode.UNAUTHORIZED.getCode(), "未登录");
        }
        IPage<ApprovalTask> page = service.pageTodo(user.getId(),
                user.getRoles() == null ? Set.of() : new java.util.HashSet<>(user.getRoles()),
                user.getUsername(), bizType, current, size);
        return R.ok(PageResult.of(page.getRecords(), page.getTotal(), current, size));
    }

    /** 已办分页（record 按 approver=当前用户查询）。 */
    @PreAuthorize(PERM_DONE)
    @GetMapping("/done")
    public R<PageResult<ApprovalDoneVO>> done(@RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String bizType) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return R.fail(ResultCode.UNAUTHORIZED.getCode(), "未登录");
        }
        IPage<ApprovalDoneVO> page = service.pageDone(userId, bizType, current, size);
        return R.ok(PageResult.of(page.getRecords(), page.getTotal(), current, size));
    }

    /** 任务详情（节点链时间轴 + record + 候选人可见性）。 */
    @PreAuthorize("isAuthenticated() and (@authz.hasPerm(authentication,'approval:todo') "
            + "or @authz.hasPerm(authentication,'approval:done'))")
    @GetMapping("/{taskId}/detail")
    public R<ApprovalTaskDetailVO> detail(@PathVariable Long taskId) {
        LoginUser user = UserContext.get();
        if (user == null) {
            return R.fail(ResultCode.UNAUTHORIZED.getCode(), "未登录");
        }
        ApprovalTaskDetailVO vo = service.detail(taskId, user.getId(),
                user.getRoles() == null ? Set.of() : new java.util.HashSet<>(user.getRoles()),
                user.getUsername());
        if (vo == null) {
            return R.fail(ResultCode.NOT_FOUND.getCode(), "审批任务不存在：" + taskId);
        }
        return R.ok(vo);
    }

    /** 节点链时间轴（详情页取数，设计 §6.1）。 */
    @PreAuthorize("isAuthenticated() and (@authz.hasPerm(authentication,'approval:todo') "
            + "or @authz.hasPerm(authentication,'approval:done'))")
    @GetMapping("/{taskId}/nodes")
    public R<java.util.List<ApprovalTaskDetailVO.NodeItem>> nodes(@PathVariable Long taskId) {
        LoginUser user = UserContext.get();
        if (user == null) {
            return R.fail(ResultCode.UNAUTHORIZED.getCode(), "未登录");
        }
        ApprovalTaskDetailVO vo = service.detail(taskId, user.getId(),
                user.getRoles() == null ? Set.of() : new java.util.HashSet<>(user.getRoles()),
                user.getUsername());
        if (vo == null) {
            return R.fail(ResultCode.NOT_FOUND.getCode(), "审批任务不存在：" + taskId);
        }
        return R.ok(vo.getNodes());
    }

    /**
     * 兼容端点（退役中）：旧任务筛选列表（bizType/status 过滤）。
     * P3 既有 /search 语义保留一版，新前端工作台走 /todo（服务端候选人过滤）。
     */
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

    /** 同意（comment 可空；网关内候选人校验，非候选→2004；并发冲突→3002）。 */
    @PreAuthorize(PERM_APPROVE)
    @PostMapping("/{taskId}/approve")
    public R<Boolean> approve(@PathVariable Long taskId, @RequestBody(required = false) CommentReq req) {
        approvalGateway.callback(taskId, ApprovalDecision.APPROVED,
                req == null ? null : req.getComment());
        return R.ok(true);
    }

    /** 驳回（comment 必填双拦截：此处第一道 + 网关第二道）。 */
    @PreAuthorize(PERM_APPROVE)
    @PostMapping("/{taskId}/reject")
    public R<Boolean> reject(@PathVariable Long taskId, @RequestBody(required = false) CommentReq req) {
        String comment = req == null ? null : req.getComment();
        if (comment == null || comment.isBlank()) {
            return R.fail(ResultCode.PARAM_ERROR.getCode(), "驳回必须填写审批意见");
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
