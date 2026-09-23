package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 商品通用状态：0=正常 / 1=停用。
 *
 * <p>归属字段：spu.status、sku.status（#33 枚举 name 契约：API 层统一
 * 序列化为 name、请求收 name/数值）。</p>
 */
public enum ProductStatus implements IEnum<Integer> {
    NORMAL(0, "正常"),
    DISABLED(1, "停用");

    private final int code;
    private final String desc;

    ProductStatus(int code, String desc) {
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
