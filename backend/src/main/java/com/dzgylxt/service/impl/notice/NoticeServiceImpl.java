package com.dzgylxt.service.impl.notice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.entity.system.UserNotice;
import com.dzgylxt.mapper.system.UserNoticeMapper;
import com.dzgylxt.service.INoticeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 站内通知服务实现（P4 设计 §3.2/§3.3）。
 *
 * <p>事务解耦：发送点由审批引擎统一置于 {@code TransactionSynchronization.afterCommit}；
 * 本服务写入自带 try-catch，失败仅 WARN 不抛出（AC③ 通知失败不回滚审批事务）。</p>
 */
@Slf4j
@Service
public class NoticeServiceImpl extends ServiceImpl<UserNoticeMapper, UserNotice> implements INoticeService {

    /** 终态通知正文截断长度（设计 §3.1：意见摘要 ≤500 字）。 */
    private static final int CONTENT_MAX_LEN = 500;

    @Override
    public void send(Long userId, String title, String content, String bizType, Long bizId) {
        if (userId == null) {
            return;
        }
        try {
            UserNotice notice = new UserNotice();
            notice.setUserId(userId);
            notice.setTitle(truncate(title, 200));
            notice.setContent(truncate(content, CONTENT_MAX_LEN));
            notice.setBizType(bizType);
            notice.setBizId(bizId);
            notice.setChannel("site");
            notice.setReadFlag(0);
            save(notice);
        } catch (Exception e) {
            // 通知失败不回滚审批事务（AC③）：WARN 日志 + 计数埋点（v1.x 接入监控）
            log.warn("[P4-Notice] 站内通知发送失败 userId={} title={} bizType={} bizId={}",
                    userId, title, bizType, bizId, e);
        }
    }

    @Override
    public long unreadCount(Long userId) {
        if (userId == null) {
            return 0;
        }
        return count(new LambdaQueryWrapper<UserNotice>()
                .eq(UserNotice::getUserId, userId)
                .eq(UserNotice::getReadFlag, 0));
    }

    @Override
    public boolean markRead(Long noticeId, Long userId) {
        if (noticeId == null || userId == null) {
            return false;
        }
        UserNotice notice = getById(noticeId);
        // 归属校验：非本人通知拒绝标记（2004 由 Controller 层转换）
        if (notice == null || !userId.equals(notice.getUserId())) {
            return false;
        }
        notice.setReadFlag(1);
        return updateById(notice);
    }

    @Override
    public int markAllRead(Long userId) {
        if (userId == null) {
            return 0;
        }
        UserNotice patch = new UserNotice();
        patch.setReadFlag(1);
        return baseMapper.update(patch, new LambdaQueryWrapper<UserNotice>()
                .eq(UserNotice::getUserId, userId)
                .eq(UserNotice::getReadFlag, 0));
    }

    @Override
    public IPage<UserNotice> pageByUser(Long userId, long current, long size) {
        return page(new Page<>(current, size), new LambdaQueryWrapper<UserNotice>()
                .eq(UserNotice::getUserId, userId)
                .orderByDesc(UserNotice::getId));
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
