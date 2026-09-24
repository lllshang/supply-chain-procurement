package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 付款状态（线下付款，状态回写；R5/R6 修订：无审批、删除 REJECTED，补 PARTIAL）。
 *
 * <p>流转：UNPAID →(累计实付 &lt; 应付) PARTIAL →(累计实付 == 应付) PAID。
 * 驳回在结算审批层，付款登记无审批，故无 REJECTED。</p>
 */
public enum PaymentStatus implements IEnum<Integer> {
    UNPAID(0, "未付款"),
    PARTIAL(1, "部分付款"),
    PAID(2, "已付款");

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
