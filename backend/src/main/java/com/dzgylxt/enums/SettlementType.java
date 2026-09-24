package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 结算类型：物料 / 服务 / 预付款（R4）。
 */
public enum SettlementType implements IEnum<Integer> {
    MATERIAL(0, "物料结算"),
    SERVICE(1, "服务结算"),
    /** R4：预付款结算——订单发起（发货前），无需到货单；审批通过核销预算，尾款自动抵扣。 */
    PREPAYMENT(2, "预付款结算");

    private final int code;
    private final String desc;

    SettlementType(int code, String desc) {
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
