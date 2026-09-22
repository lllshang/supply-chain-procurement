package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 供应商黑名单标记：0=否，1=是。
 */
public enum BlacklistFlag implements IEnum<Integer> {
    NO(0, "否"),
    YES(1, "是");

    private final int code;
    private final String desc;

    BlacklistFlag(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    @JsonValue
    public Integer getValue() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
