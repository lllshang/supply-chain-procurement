package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 结算状态。
 */
public enum SettlementStatus implements IEnum<Integer> {
    PENDING(0, "待结算"),
    SETTLED(1, "已结算"),
    PARTIAL(2, "部分结算");

    private final int code;
    private final String desc;

    SettlementStatus(int code, String desc) {
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
