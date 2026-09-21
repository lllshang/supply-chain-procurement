package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 采购订单状态机。
 */
public enum OrderStatus implements IEnum<Integer> {
    CREATED(0, "已创建(占用预算)"),
    PARTIAL_RECEIVED(1, "部分到货"),
    RECEIVED(2, "已到货"),
    SETTLED(3, "已结算"),
    CANCELLED(4, "已取消(释放预算)"),
    PAID(5, "已付款");

    private final int code;
    private final String desc;

    OrderStatus(int code, String desc) {
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
