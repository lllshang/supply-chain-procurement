package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 价格库审核状态（P3 设计 §1.4，price_history.audit_status）。
 *
 * <p>#33 name 契约：API 层序列化为枚举名（不加 @JsonValue）。</p>
 */
public enum PriceAuditStatus implements IEnum<Integer> {
    PENDING(0, "待审"),
    APPROVED(1, "通过"),
    REJECTED(2, "驳回");

    private final int code;
    private final String desc;

    PriceAuditStatus(int code, String desc) {
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
