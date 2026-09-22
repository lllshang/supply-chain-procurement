package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 预算科目类型：1=支出，2=收入。
 */
public enum SubjectType implements IEnum<Integer> {
    EXPENSE(1, "支出"),
    INCOME(2, "收入");

    private final int code;
    private final String desc;

    SubjectType(int code, String desc) {
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
