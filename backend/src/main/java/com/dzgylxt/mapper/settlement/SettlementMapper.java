package com.dzgylxt.mapper.settlement;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.settlement.Settlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/** 结算 Mapper（含订单维度聚合口径）。 */
@Mapper
public interface SettlementMapper extends BaseMapper<Settlement> {

    /** 订单的全部结算单（含 PENDING，重复结算与累计口径用）。 */
    @Select("SELECT * FROM settlement WHERE deleted = 0 AND order_id = #{orderId} ORDER BY id ASC")
    List<Settlement> selectByOrder(@Param("orderId") Long orderId);

    /** 订单已结算金额（仅 SETTLED；结清判定口径）。 */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM settlement"
            + " WHERE deleted = 0 AND order_id = #{orderId} AND status = 1")
    BigDecimal sumSettledAmount(@Param("orderId") Long orderId);

    /** 到货单是否已有结算单（重复结算拦截键）。 */
    @Select("SELECT COUNT(1) FROM settlement WHERE deleted = 0 AND arrival_id = #{arrivalId}")
    boolean existsByArrival(@Param("arrivalId") Long arrivalId);
}
