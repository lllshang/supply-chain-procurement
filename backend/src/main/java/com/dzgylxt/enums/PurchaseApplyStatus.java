package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 采购申请状态机。
 */
public enum PurchaseApplyStatus implements IEnum<Integer> {
    DRAFT(0, "草稿"),
    BUDGET_PENDING(1, "预算待审"),
    PURCHASE_PENDING(2, "采购待审"),
    APPROVED(3, "已审批"),
    REJECTED(4, "已驳回"),
    PARTIAL_ORDER(5, "部分转单"),
    FULL_ORDER(6, "全部转单"),
    /** P3 设计 §2 行10：作废（未转单完成前可作废，释放全部预算占用）。 */
    CLOSED(7, "已作废");

    private final int code;
    private final String desc;

    PurchaseApplyStatus(int code, String desc) {
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
