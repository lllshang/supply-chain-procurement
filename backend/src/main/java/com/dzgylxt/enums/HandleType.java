package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 差异处理方式（P2 §1.4）：0=接受 / 1=退货 / 2=补货。
 */
public enum HandleType implements IEnum<Integer> {
    ACCEPT(0, "接受"),
    RETURN(1, "退货"),
    REPLENISH(2, "补货");

    private final int code;
    private final String desc;

    HandleType(int code, String desc) {
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
