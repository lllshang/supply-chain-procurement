package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 到货验收状态。
 */
public enum ArrivalStatus implements IEnum<Integer> {
    ARRIVAL_CONFIRMED(0, "到货确认"),
    PARTIAL_STORED(1, "部分入库"),
    STORED(2, "已入库");

    private final int code;
    private final String desc;

    ArrivalStatus(int code, String desc) {
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
