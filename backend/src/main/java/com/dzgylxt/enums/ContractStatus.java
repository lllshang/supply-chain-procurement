package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 合同状态机：仅 EFFECTIVE 可发起订单。
 */
public enum ContractStatus implements IEnum<Integer> {
    DRAFT(0, "草稿"),
    PENDING_APPROVAL(1, "待审批"),
    EFFECTIVE(2, "生效中"),
    EXPIRED(3, "已过期"),
    TERMINATED(4, "已终止");

    private final int code;
    private final String desc;

    ContractStatus(int code, String desc) {
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
