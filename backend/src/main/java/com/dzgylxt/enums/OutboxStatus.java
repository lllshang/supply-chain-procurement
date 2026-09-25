package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * Outbox 事件状态（数值枚举，API 层序列化为数值，QA #33 数值组）。
 *
 * <p>归属字段：outbox_event.status。0=PENDING（待投递），1=SENT（已投递），
 * 2=RETRY（重试），3=FAILED（失败）。</p>
 */
public enum OutboxStatus implements IEnum<Integer> {
    PENDING(0, "待投递"),
    SENT(1, "已投递"),
    RETRY(2, "重试"),
    FAILED(3, "失败");

    private final int code;
    private final String desc;

    OutboxStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    @com.fasterxml.jackson.annotation.JsonValue
    public Integer getValue() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
