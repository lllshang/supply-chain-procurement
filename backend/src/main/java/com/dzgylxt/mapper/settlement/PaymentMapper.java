package com.dzgylxt.mapper.settlement;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.settlement.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/** 付款登记 Mapper。 */
@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {

    /** 结算单的全部付款单（PAID 累计与展示）。 */
    @Select("SELECT * FROM payment WHERE deleted = 0 AND settlement_id = #{settlementId} ORDER BY id ASC")
    List<Payment> selectBySettlement(@Param("settlementId") Long settlementId);

    /** 结算单已付累计（仅 PAID；订单付清判定口径。R5：PaymentStatus.PAID=2）。 */
    @Select("SELECT COALESCE(SUM(pay_amount), 0) FROM payment"
            + " WHERE deleted = 0 AND settlement_id = #{settlementId} AND status = 2")
    BigDecimal sumPaidAmount(@Param("settlementId") Long settlementId);

    /** R5：订单已付累计（关联结算单的 PAID 付款；付清进度 paidProgress 口径）。 */
    @Select("SELECT COALESCE(SUM(p.pay_amount), 0) FROM payment p"
            + " JOIN settlement s ON p.settlement_id = s.id"
            + " WHERE p.deleted = 0 AND s.deleted = 0 AND s.order_id = #{orderId} AND p.status = 2")
    BigDecimal sumPaidAmountByOrder(@Param("orderId") Long orderId);

    /**
     * 结算单付款承诺累计（QA #39：计入在途 + 已付——UNPAID/PARTIAL 在途 + PAID 实付，
     * 防止多笔在途付款合计超出结算金额。R5：PaymentStatus 删除 REJECTED，
     * 承诺 = 全部非删除付款 = status IN (0,1,2)）。
     */
    @Select("SELECT COALESCE(SUM(pay_amount), 0) FROM payment"
            + " WHERE deleted = 0 AND settlement_id = #{settlementId} AND status IN (0, 1, 2)")
    BigDecimal sumCommittedAmount(@Param("settlementId") Long settlementId);
}
