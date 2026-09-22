package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 履约调整业务对象（P2 §1.4）：0=订单 / 1=到货。
 */
public enum AdjustBizType implements IEnum<Integer> {
    ORDER(0, "订单"),
    ARRIVAL(1, "到货");

    private final int code;
    private final String desc;

    AdjustBizType(int code, String desc) {
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
