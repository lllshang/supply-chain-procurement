package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 审批节点状态（数值枚举，API 层序列化为数值，QA #33 数值组）。
 *
 * <p>归属字段：approval_node.status、ApprovalTaskDetailVO.NodeItem.status。
 * 0=待审，1=已审结（通过时回填 approver/action），2=已跳过（任务驳回后未走到的节点）。</p>
 */
public enum ApprovalNodeStatus implements IEnum<Integer> {
    PENDING(0, "待审"),
    DONE(1, "已审结"),
    SKIPPED(2, "已跳过");

    private final int code;
    private final String desc;

    ApprovalNodeStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    @com.fasterxml.jackson.annotation.JsonValue
    public Integer getValue() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
