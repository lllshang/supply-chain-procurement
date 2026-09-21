package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 预算头状态。
 */
public enum BudgetHeaderStatus implements IEnum<Integer> {
    DRAFT(0, "草稿"),
    ACTIVE(1, "生效"),
    ARCHIVED(2, "已归档");

    private final int code;
    private final String desc;

    BudgetHeaderStatus(int code, String desc) {
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
