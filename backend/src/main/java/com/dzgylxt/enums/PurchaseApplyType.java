package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 采购申请类型（三链路）：标准/项目、日常/框架、线下补录。
 */
public enum PurchaseApplyType implements IEnum<Integer> {
    STANDARD(0, "标准/项目采购"),
    DAILY(1, "日常/框架采购"),
    OFFLINE(2, "线下补录");

    private final int code;
    private final String desc;

    PurchaseApplyType(int code, String desc) {
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
