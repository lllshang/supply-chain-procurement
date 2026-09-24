package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 付款状态（线下付款，状态回写；R5/R6 修订：无审批、删除 REJECTED）。
 *
 * <p>PB-01（规格 §10，PM 裁决 b676143 采口径 B）：部分付款属<b>结算维度</b>而非付款单
 * 维度——付款记录无中间态，确认即整单 PAID；"未付款/部分付款/已付清"由结算单派生字段
 * {@code Settlement.payStatus/paidProgress} 按 Σ已确认付款 vs（结算应付 − 已抵扣预付）
 * 计算（§6.11.1 L906 / PAY-06 L918：付款状态按累计实付金额自动计算）。</p>
 *
 * <p>历史库 code=2（旧"已付款"）经 scripts/sql/p3b_schema.sql 幂等迁移重编号为 1。</p>
 */
public enum PaymentStatus implements IEnum<Integer> {
    UNPAID(0, "未付款"),
    PAID(1, "已付款");

    private final int code;
    private final String desc;

    PaymentStatus(int code, String desc) {
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
