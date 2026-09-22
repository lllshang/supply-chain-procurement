package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 差异处理状态（P2 §1.4）：0=待处理 / 1=已完成。
 */
public enum HandleStatus implements IEnum<Integer> {
    PENDING(0, "待处理"),
    DONE(1, "已完成");

    private final int code;
    private final String desc;

    HandleStatus(int code, String desc) {
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
