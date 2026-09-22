package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 履约调整类型（P2 §1.4）：0=差异 / 1=退货 / 2=补货 / 3=变更。
 */
public enum AdjustType implements IEnum<Integer> {
    DIFF(0, "差异"),
    RETURN(1, "退货"),
    REPLENISH(2, "补货"),
    CHANGE(3, "变更");

    private final int code;
    private final String desc;

    AdjustType(int code, String desc) {
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
