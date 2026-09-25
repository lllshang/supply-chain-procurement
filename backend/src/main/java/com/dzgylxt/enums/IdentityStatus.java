package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 身份 / 权限对象状态（数值枚举，API 层序列化为数值，QA #33 数值组）。
 *
 * <p>归属字段：sys_dept.status、sys_role.status。0=正常，1=停用。</p>
 */
public enum IdentityStatus implements IEnum<Integer> {
    NORMAL(0, "正常"),
    DISABLED(1, "停用");

    private final int code;
    private final String desc;

    IdentityStatus(int code, String desc) {
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
