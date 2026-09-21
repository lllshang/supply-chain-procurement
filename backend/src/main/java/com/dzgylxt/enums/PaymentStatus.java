package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 付款状态（线下付款，状态回写）。
 */
public enum PaymentStatus implements IEnum<Integer> {
    UNPAID(0, "未付款"),
    PAID(1, "已付款"),
    REJECTED(2, "已驳回");

    private final int code;
    private final String desc;

    PaymentStatus(int code, String desc) {
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
