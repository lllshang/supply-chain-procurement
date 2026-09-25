package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 供应商-商品绑定状态（数值枚举，API 层序列化为数值，QA #33 数值组）。
 *
 * <p>归属字段：supplier_sku.status、SupplierSkuRespVO.status。0=正常，1=停用。</p>
 */
public enum SupplierSkuStatus implements IEnum<Integer> {
    NORMAL(0, "正常"),
    DISABLED(1, "停用");

    private final int code;
    private final String desc;

    SupplierSkuStatus(int code, String desc) {
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
