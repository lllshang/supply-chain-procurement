package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 菜单状态（数值枚举，API 层序列化为数值，QA #33 数值组）。
 *
 * <p>归属字段：sys_menu.status。0=显示，1=隐藏。</p>
 */
public enum MenuStatus implements IEnum<Integer> {
    VISIBLE(0, "显示"),
    HIDDEN(1, "隐藏");

    private final int code;
    private final String desc;

    MenuStatus(int code, String desc) {
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
