package com.dzgylxt.controller.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dzgylxt.common.PageResult;
import com.dzgylxt.common.R;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.system.UserNotice;
import com.dzgylxt.security.UserContext;
import com.dzgylxt.service.INoticeService;
import com.dzgylxt.common.SecurityConstants;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知（P4 设计 §6.3）。
 *
 * <p><b>权限豁免说明（显式例外登记，非默许裸奔）</b>：三端点均为纯个人数据且无业务语义，
 * {@code isAuthenticated()} + 服务端归属过滤（user_id=当前用户，个人数据归属过滤）即满足
 * 纵深防御；不设业务权限键（避免给全员发权限键的运维负担）。</p>
 */
@RestController
@RequestMapping("/api/v1/notices")
public class NoticeController {

    private final INoticeService noticeService;

    public NoticeController(INoticeService noticeService) {
        this.noticeService = noticeService;
    }

    /** 未读数（红点角标）。 */
    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        return R.ok(noticeService.unreadCount(UserContext.getCurrentUserId()));
    }

    /** 分页列表（强制 user_id=当前用户，个人数据归属过滤）。 */
    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping
    public R<PageResult<UserNotice>> page(@RequestParam(defaultValue = "1") long current,
                                          @RequestParam(defaultValue = "20") long size) {
        IPage<UserNotice> page = noticeService.pageByUser(UserContext.getCurrentUserId(), current, size);
        return R.ok(PageResult.of(page.getRecords(), page.getTotal(), current, size));
    }

    /** 标记已读（归属校验，非本人 → 2004）。 */
    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping("/{id}/read")
    public R<Boolean> markRead(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        if (!noticeService.markRead(id, userId)) {
            return R.fail(ResultCode.FORBIDDEN.getCode(), "无权操作该通知");
        }
        return R.ok(true);
    }

    /** 全部已读。 */
    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping("/read-all")
    public R<Integer> readAll() {
        return R.ok(noticeService.markAllRead(UserContext.getCurrentUserId()));
    }
}
