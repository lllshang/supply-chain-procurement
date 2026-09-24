package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 结算状态。
 */
public enum SettlementStatus implements IEnum<Integer> {
    PENDING(0, "待结算"),
    SETTLED(1, "已结算"),
    PARTIAL(2, "部分结算"),
    /** B9：作废——误建/超建结算单冲正，释放承诺盘子（PENDING→VOIDED，自动从 committed 口径排除）。 */
    VOIDED(3, "已作废");

    private final int code;
    private final String desc;

    SettlementStatus(int code, String desc) {
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
