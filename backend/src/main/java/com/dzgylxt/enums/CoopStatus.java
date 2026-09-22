package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 供应商合作状态：0=正常，1=停用，2=冻结。
 *
 * <p>与资质状态 {@link SupplierStatus} 解耦：资质审核中不应误伤合作状态。</p>
 */
public enum CoopStatus implements IEnum<Integer> {
    NORMAL(0, "正常"),
    DISABLED(1, "停用"),
    FROZEN(2, "冻结");

    private final int code;
    private final String desc;

    CoopStatus(int code, String desc) {
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
