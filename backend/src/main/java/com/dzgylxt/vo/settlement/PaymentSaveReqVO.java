package com.dzgylxt.vo.settlement;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 付款登记创建/重提请求（P3 设计 §2.2）。
 *
 * <p>两阶段：创建（UNPAID 待财务审）→ submit（PAYMENT 审批）→ 通过（仍 UNPAID 待确认）
 * → confirmPayment（线下付款登记凭证 → PAID）。Q3 线下付款无资金通道。</p>
 */
@Data
public class PaymentSaveReqVO implements Serializable {
    private Long settlementId;
    private BigDecimal payAmount;
    /** 付款方式（银行转账/承兑等，自由文本） */
    private String payMethod;
    private String remark;
}
