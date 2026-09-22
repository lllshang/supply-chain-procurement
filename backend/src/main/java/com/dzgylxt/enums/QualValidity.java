package com.dzgylxt.enums;

/**
 * 资质有效期状态（派生，不落库）。
 *
 * <p>由 {@code supplier_qual.expire_at} + 预警提前天数实时计算：
 * 未到期=VALID，临近到期（≤ 预警天数）=EXPIRING，已过期=EXPIRED。</p>
 */
public enum QualValidity {
    VALID("有效"),
    EXPIRING("即将到期"),
    EXPIRED("已过期");

    private final String desc;

    QualValidity(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
