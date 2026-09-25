package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 到货差异类型（P2 §1.4）：0=无差异 / 1=短缺 / 2=破损；P4 R3b 增 3=超收（授权放行）。
 */
public enum DiffType implements IEnum<Integer> {
    NONE(0, "无差异"),
    SHORTAGE(1, "短缺"),
    DAMAGED(2, "破损"),
    OVER(3, "超收");

    private final int code;
    private final String desc;

    DiffType(int code, String desc) {
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
