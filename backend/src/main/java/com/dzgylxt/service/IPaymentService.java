package com.dzgylxt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.settlement.Payment;
import com.dzgylxt.vo.settlement.StatementVO;

import java.time.LocalDate;

/**
 * 付款登记服务（P3 设计 §2.2 / T05；R6 修订：付款登记免审批）。
 *
 * <p>R6（PRD §6.11.1 L903：审批止于结算）：取消 PAYMENT bizType——付款创建后
 * 直接待登记，财务线下付款后 {@link #confirmPayment} 登记凭证确认 → PAID
 * 并回写结算/订单（全部付清→订单 PAID）。{@code reviewed_by/at} 字段停用保留兼容。
 * 付款登记确认无预算动作（核销已在结算完成）。</p>
 */
public interface IPaymentService extends IService<Payment> {

    /** 创建付款单（仅 SETTLED 结算单；累计付款 ≤ 结算金额；生成 pay_no；R6：无审批环节）。 */
    Long createPayment(com.dzgylxt.vo.settlement.PaymentSaveReqVO req);

    /** 修改（未确认付款单）。 */
    void updatePayment(Long id, com.dzgylxt.vo.settlement.PaymentSaveReqVO req);

    /** 财务线下付款登记确认（R6：免审批直接登记）：置 PAID + confirmed_by/at + 凭证/日期；回写结算、订单全部付清→PAID。 */
    void confirmPayment(Long id, String voucherFile, LocalDate payDate);

    /** 供应商对账单（应付=Σ结算、已付=Σ付款、差额+明细清单）。 */
    StatementVO statement(Long supplierId, LocalDate from, LocalDate to);
}
