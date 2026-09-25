package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 同步对账状态（数值枚举，API 层序列化为数值，QA #33 数值组）。
 *
 * <p>归属字段：sync_reconcile.status。</p>
 *
 * <p><b>TODO(B5) 语义待确认</b>：代码库中 {@code sync_reconcile.status} 无任何 Java 写入点
 * （仅实体 + Mapper + 建表语句，且建表注释未给出取值），其枚举值无法从源码/SQL 实证确定。
 * 以下 0/1/2 为参照同模块 Outbox 事件状态机（待处理 / 成功 / 失败）推测，
 * <b>非源码实证</b>，待与集成模块负责人核对后可能调整。当前定义可保证「整型 ↔ 枚举」双向映射不抛错，
 * 且不改变 API 数值线格式。</p>
 */
public enum SyncStatus implements IEnum<Integer> {
    PENDING(0, "待同步"),
    SUCCESS(1, "成功"),
    FAILED(2, "失败");

    private final int code;
    private final String desc;

    SyncStatus(int code, String desc) {
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
