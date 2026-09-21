package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 审批单据状态机。
 */
public enum ApprovalStatus implements IEnum<Integer> {
    CREATED(0, "已创建"),
    IN_PROGRESS(1, "审批中"),
    APPROVED(2, "通过"),
    REJECTED(3, "驳回"),
    CALLBACK_DONE(4, "回调完成");

    private final int code;
    private final String desc;

    ApprovalStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    public Integer getValue() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
