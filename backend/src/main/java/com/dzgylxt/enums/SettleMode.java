package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 结算方式（P3 设计 §1.4，settlement.settle_mode）。
 *
 * <p>#33 name 契约：API 层序列化为枚举名（不加 @JsonValue）。</p>
 */
public enum SettleMode implements IEnum<Integer> {
    ONE_TIME(0, "一次性"),
    PHASE(1, "阶段"),
    FINAL(2, "尾款");

    private final int code;
    private final String desc;

    SettleMode(int code, String desc) {
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
