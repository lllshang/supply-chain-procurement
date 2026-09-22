package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 价格规则类型：1=最低限价，2=最高限价，3=区间，4=公式。
 */
public enum PriceRuleType implements IEnum<Integer> {
    MIN_LIMIT(1, "最低限价"),
    MAX_LIMIT(2, "最高限价"),
    RANGE(3, "区间"),
    FORMULA(4, "公式");

    private final int code;
    private final String desc;

    PriceRuleType(int code, String desc) {
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
