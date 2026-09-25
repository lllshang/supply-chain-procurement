package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 资质状态：0=待审，1=通过，2=驳回。
 *
 * <p>与 {@link SupplierStatus} 数值完全一致，但语义化命名用于资质审核闭环
 * （待审 → 通过 / 驳回 → 待审重提）。</p>
 */
public enum QualStatus implements IEnum<Integer> {
    PENDING(0, "待审"),
    APPROVED(1, "通过"),
    REJECTED(2, "驳回");

    private final int code;
    private final String desc;

    QualStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    @com.fasterxml.jackson.annotation.JsonValue
    public Integer getValue() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
