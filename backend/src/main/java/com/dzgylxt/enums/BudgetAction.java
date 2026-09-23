package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 预算流水动作（P3 设计 §1.4，budget_occupy_log.action）。
 *
 * <p>#33 name 契约：API 层序列化为枚举名（不加 @JsonValue）。</p>
 */
public enum BudgetAction implements IEnum<Integer> {
    OCCUPY(0, "占用"),
    RELEASE(1, "释放"),
    WRITE_OFF(2, "核销"),
    ADJUST(3, "调整");

    private final int code;
    private final String desc;

    BudgetAction(int code, String desc) {
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
