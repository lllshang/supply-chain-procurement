package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 供应商状态：0=资质审核中，1=通过，2=驳回。
 */
public enum SupplierStatus implements IEnum<Integer> {
    PENDING(0, "资质审核中"),
    APPROVED(1, "通过"),
    REJECTED(2, "驳回");

    private final int code;
    private final String desc;

    SupplierStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    @JsonValue
    public Integer getValue() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
