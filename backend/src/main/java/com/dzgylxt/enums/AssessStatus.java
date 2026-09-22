package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 服务考核单状态（P2 §1.4）：0=正常 / 1=作废。
 */
public enum AssessStatus implements IEnum<Integer> {
    NORMAL(0, "正常"),
    VOIDED(1, "作废");

    private final int code;
    private final String desc;

    AssessStatus(int code, String desc) {
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
