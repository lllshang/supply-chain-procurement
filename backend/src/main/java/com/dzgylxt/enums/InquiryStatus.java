package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 询价单状态。
 */
public enum InquiryStatus implements IEnum<Integer> {
    DRAFT(0, "草稿"),
    PUBLISHED(1, "已发布"),
    CLOSED(2, "已截标"),
    CANCELLED(3, "已取消");

    private final int code;
    private final String desc;

    InquiryStatus(int code, String desc) {
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
