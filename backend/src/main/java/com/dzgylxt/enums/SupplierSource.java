package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 供应商来源：0=平台录入，1=H5提交，2=导入。
 */
public enum SupplierSource implements IEnum<Integer> {
    PLATFORM(0, "平台录入"),
    H5(1, "H5提交"),
    IMPORT(2, "导入");

    private final int code;
    private final String desc;

    SupplierSource(int code, String desc) {
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
