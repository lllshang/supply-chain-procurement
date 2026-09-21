package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 数据权限范围：本人 / 本部门 / 本部门及子部门 / 全部。
 */
public enum DataScope implements IEnum<Integer> {
    SELF(0, "本人"),
    DEPT(1, "本部门"),
    DEPT_AND_CHILD(2, "本部门及子部门"),
    ALL(3, "全部");

    private final int code;
    private final String desc;

    DataScope(int code, String desc) {
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
