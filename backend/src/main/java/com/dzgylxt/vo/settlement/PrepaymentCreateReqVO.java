package com.dzgylxt.vo.settlement;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 预付款结算创建请求（R4：订单发起，无需到货）。
 *
 * <p>预付款金额从订单条款发起，发货前即可结算并走 SETTLEMENT 审批核销；
 * 尾款结算时自动扣减本订单已付预付款合计（{@code Settlement.prepaymentDeduction}）。</p>
 */
@Data
public class PrepaymentCreateReqVO implements Serializable {
    /** 预付款金额（必须 &gt; 0；累计预付款 ≤ 订单有效金额） */
    private BigDecimal amount;
    /** 备注 */
    private String remark;
}
