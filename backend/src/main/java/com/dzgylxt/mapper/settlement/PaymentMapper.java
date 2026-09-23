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

    /** 结算单已付累计（仅 PAID；订单付清判定口径）。 */
    @Select("SELECT COALESCE(SUM(pay_amount), 0) FROM payment"
            + " WHERE deleted = 0 AND settlement_id = #{settlementId} AND status = 1")
    BigDecimal sumPaidAmount(@Param("settlementId") Long settlementId);

    /**
     * 结算单付款承诺累计（QA #39：计入非 REJECTED——PAID 实付 + UNPAID 在途，
     * 防止多笔在途付款合计超出结算金额）。
     */
    @Select("SELECT COALESCE(SUM(pay_amount), 0) FROM payment"
            + " WHERE deleted = 0 AND settlement_id = #{settlementId} AND status != 2")
    BigDecimal sumCommittedAmount(@Param("settlementId") Long settlementId);
}
