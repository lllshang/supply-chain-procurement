package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 常购清单状态（P2 §1.4）：0=正常 / 1=停用（停用 SKU 不可带入申请）。
 */
public enum FrequentStatus implements IEnum<Integer> {
    NORMAL(0, "正常"),
    DISABLED(1, "停用");

    private final int code;
    private final String desc;

    FrequentStatus(int code, String desc) {
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
