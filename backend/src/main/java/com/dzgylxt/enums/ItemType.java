package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 行级类型：0=物料 / 1=服务（P2 §1.4）。
 *
 * <p>归属字段：purchase_apply_item.item_type、purchase_order.order_type、
 * contract.contract_type（服务/综合）。转单按行级类型拆单。</p>
 */
public enum ItemType implements IEnum<Integer> {
    MATERIAL(0, "物料"),
    SERVICE(1, "服务");

    private final int code;
    private final String desc;

    ItemType(int code, String desc) {
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
