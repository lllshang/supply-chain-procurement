package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 供应商-商品绑定范围：0=不限定（优选/建议），1=限定报价接单范围（强制）。
 *
 * <p>默认取 0，绑定为"优选/建议"关系而非强制，不卡 P2 询价。</p>
 */
public enum BindScope implements IEnum<Integer> {
    UNLIMITED(0, "不限定"),
    LIMITED(1, "限定报价接单范围");

    private final int code;
    private final String desc;

    BindScope(int code, String desc) {
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
