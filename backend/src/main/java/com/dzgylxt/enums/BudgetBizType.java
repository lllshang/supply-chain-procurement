package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 预算流水业务类型（P3 设计 §1.4，budget_occupy_log.biz_type）。
 *
 * <p>#33 name 契约：API 层序列化为枚举名（不加 @JsonValue）。</p>
 */
public enum BudgetBizType implements IEnum<Integer> {
    APPLY(1, "采购申请"),
    AWARD(2, "定标"),
    ORDER(3, "采购订单"),
    SETTLEMENT(4, "结算"),
    ADJUST(5, "预算调整");

    private final int code;
    private final String desc;

    BudgetBizType(int code, String desc) {
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
