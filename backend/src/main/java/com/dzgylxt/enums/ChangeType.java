package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 订单变更类型（P2 §1.4）：1=数量 / 2=价格 / 3=明细 / 4=取消。
 */
public enum ChangeType implements IEnum<Integer> {
    QTY(1, "数量"),
    PRICE(2, "价格"),
    ITEM(3, "明细"),
    CANCEL(4, "取消");

    private final int code;
    private final String desc;

    ChangeType(int code, String desc) {
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
