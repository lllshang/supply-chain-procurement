package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 报价状态。
 */
public enum QuotationStatus implements IEnum<Integer> {
    SUBMITTED(0, "已提交"),
    ACCEPTED(1, "已采纳"),
    REJECTED(2, "已否决");

    private final int code;
    private final String desc;

    QuotationStatus(int code, String desc) {
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
