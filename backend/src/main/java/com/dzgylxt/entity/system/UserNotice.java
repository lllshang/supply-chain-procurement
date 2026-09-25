package com.dzgylxt.entity.system;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 站内通知（P4 规格 §3.6 裁量：站内最小集）。
 *
 * <p>触发点：任务创建（候选审批人）/ 节点推进（下一节点候选人）/ 任务终态（申请人）。
 * 外部渠道（sms/wecom/dingtalk）Adapter 口子见 P4 设计 §3.4，v1.x 扩展不改表结构。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("user_notice")
public class UserNotice extends BaseEntity implements Serializable {

    /** 接收人（sys_user.id） */
    private Long userId;
    /** 通知标题 */
    private String title;
    /** 正文（终态通知含意见摘要，截断 500 字） */
    private String content;
    /** 关联业务类型（审批通知=bizType） */
    private String bizType;
    /** 关联业务单据/任务 id（taskId） */
    private Long bizId;
    /** 渠道：site=站内（预留：sms/wecom/dingtalk） */
    private String channel;
    /** 0=未读 1=已读（红点角标数据源） */
    private Integer readFlag;
}
