package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * SKU 计价方式：0=计件，1=计重。
 */
public enum ValuationType implements IEnum<Integer> {
    BY_PIECE(0, "计件"),
    BY_WEIGHT(1, "计重");

    private final int code;
    private final String desc;

    ValuationType(int code, String desc) {
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
