package com.dzgylxt.entity.settlement;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.common.BaseEntity;
import com.dzgylxt.enums.PaymentStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 付款登记（P3 设计 §1.3.2；线下付款，财务手动登记确认+凭证回写，Q3）。
 *
 * <p>状态机：UNPAID（创建，待财务审）→(PAYMENT 审批通过，reviewed_by/at 落，
 * 仍 UNPAID 待确认)→(confirmPayment) PAID；审批驳回→REJECTED 可修改重提。
 * 付款登记确认无预算动作（核销已在结算完成，规格 §5 行 12）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("payment")
public class Payment extends BaseEntity implements Serializable {
    private Long settlementId;
    /** 付款单号 FK-{yyyy}{MM}-{seq6}（全局唯一） */
    private String payNo;
    private BigDecimal payAmount;
    private String payMethod;
    private String voucherFile;
    /** 付款日期（登记确认时填） */
    private LocalDate payDate;
    /** 财务审核人（PAYMENT 审批回调落） */
    private Long reviewedBy;
    /** 财务审核时间 */
    private LocalDateTime reviewedAt;
    /** 线下付款登记确认人 */
    private Long confirmedBy;
    /** 登记确认时间 */
    private LocalDateTime confirmedAt;
    private PaymentStatus status;
    private String remark;
}
