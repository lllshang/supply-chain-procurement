package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 定标状态。
 */
public enum AwardStatus implements IEnum<Integer> {
    PENDING_APPROVAL(0, "待审批"),
    APPROVED(1, "已审批"),
    REJECTED(2, "已驳回");

    private final int code;
    private final String desc;

    AwardStatus(int code, String desc) {
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
