package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 订单变更状态（Q5：order_change 免审直接生效）：0=待审 / 1=生效 / 2=驳回。
 */
public enum ChangeStatus implements IEnum<Integer> {
    PENDING(0, "待审"),
    EFFECTIVE(1, "生效"),
    REJECTED(2, "驳回");

    private final int code;
    private final String desc;

    ChangeStatus(int code, String desc) {
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
