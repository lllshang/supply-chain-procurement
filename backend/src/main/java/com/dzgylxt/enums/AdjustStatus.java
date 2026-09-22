package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 履约调整状态机（P2 §2.8）：0=草稿 / 1=审批中 / 2=生效 / 3=驳回。
 *
 * <p>金额 ≤ 合同金额 5%（{@code app.adjust.approve-threshold}，可配置）免审直接生效；
 * 超阈值走 {@code FULFILLMENT_ADJUST} 审批（<!-- D5 -->）。</p>
 */
public enum AdjustStatus implements IEnum<Integer> {
    DRAFT(0, "草稿"),
    IN_APPROVAL(1, "审批中"),
    EFFECTIVE(2, "生效"),
    REJECTED(3, "驳回");

    private final int code;
    private final String desc;

    AdjustStatus(int code, String desc) {
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
