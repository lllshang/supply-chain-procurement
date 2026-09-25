package com.dzgylxt.service;

import com.dzgylxt.entity.system.UserNotice;

/**
 * 站内通知服务（P4 设计 §3）。
 *
 * <p>发送收敛单点（v1.x 外部渠道 Adapter 口子：按 {@code channel} 分发，不改审批引擎）。</p>
 */
public interface INoticeService {

    /**
     * 发送站内通知（try-catch 包裹：通知失败仅 WARN 日志，不向上抛——AC③ 通知失败不回滚审批事务）。
     *
     * @param userId  接收人（sys_user.id），空则跳过
     * @param title   标题
     * @param content 正文（终态通知含意见摘要，截断 500 字）
     * @param bizType 关联业务类型（审批通知=bizType）
     * @param bizId   关联业务单据/任务 id（taskId）
     */
    void send(Long userId, String title, String content, String bizType, Long bizId);

    /** 当前用户未读通知数（红点角标）。 */
    long unreadCount(Long userId);

    /** 标记单条已读（归属校验由调用方保证，非本人返回 false）。 */
    boolean markRead(Long noticeId, Long userId);

    /** 全部已读。 */
    int markAllRead(Long userId);

    /** 分页查询当前用户通知（个人数据归属过滤）。 */
    com.baomidou.mybatisplus.core.metadata.IPage<UserNotice> pageByUser(Long userId, long current, long size);
}
