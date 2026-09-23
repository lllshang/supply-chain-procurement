package com.dzgylxt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.settlement.Payment;
import com.dzgylxt.vo.settlement.StatementVO;

import java.time.LocalDate;

/**
 * 付款登记服务（P3 设计 §2.2 / T05）。
 *
 * <p>两阶段：创建→UNPAID（待财务审）→ submit 发 PAYMENT 审批 → 通过（reviewed_by/at 落，
 * 仍 UNPAID 待确认）/ 驳回→REJECTED 可重提 → confirmPayment（线下登记凭证+日期）→ PAID
 * 并回写结算/订单（全部付清→订单 PAID）。付款登记确认无预算动作（核销已在结算完成）。</p>
 */
public interface IPaymentService extends IService<Payment> {

    /** 创建付款单（仅 SETTLED 结算单；累计付款 ≤ 结算金额；生成 pay_no）。 */
    Long createPayment(com.dzgylxt.vo.settlement.PaymentSaveReqVO req);

    /** 修改重提（REJECTED 后）。 */
    void updatePayment(Long id, com.dzgylxt.vo.settlement.PaymentSaveReqVO req);

    /** 发起 PAYMENT 审批（财务审核）。 */
    Long submit(Long id);

    /** 财务手动登记确认：置 PAID + confirmed_by/at + 凭证/日期；回写结算、订单全部付清→PAID。 */
    void confirmPayment(Long id, String voucherFile, LocalDate payDate);

    /** 供应商对账单（应付=Σ结算、已付=Σ付款、差额+明细清单）。 */
    StatementVO statement(Long supplierId, LocalDate from, LocalDate to);
}
