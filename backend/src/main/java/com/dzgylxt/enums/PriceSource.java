package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 价格库来源（P3 设计 §1.4，price_history.source）。
 *
 * <p>#33 name 契约：API 层序列化为枚举名（不加 @JsonValue）。</p>
 */
public enum PriceSource implements IEnum<Integer> {
    QUOTATION(0, "报价"),
    AWARD(1, "定标"),
    ORDER(2, "订单"),
    MANUAL(3, "手工");

    private final int code;
    private final String desc;

    PriceSource(int code, String desc) {
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
