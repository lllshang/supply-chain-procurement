package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 价格规则引用类型：1=商品(SPU)，2=品类(product_category)。
 */
public enum PriceRefType implements IEnum<Integer> {
    SPU(1, "商品"),
    CATEGORY(2, "品类");

    private final int code;
    private final String desc;

    PriceRefType(int code, String desc) {
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
