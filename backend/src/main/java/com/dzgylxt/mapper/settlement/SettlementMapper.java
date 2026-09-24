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

    /** R4：订单已批预付款合计（仅 SETTLED 预付款结算；审批时点复算与草稿展示口径）。type=2=PREPAYMENT。 */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM settlement"
            + " WHERE deleted = 0 AND order_id = #{orderId} AND status = 1 AND type = 2")
    BigDecimal sumPrepaymentPaid(@Param("orderId") Long orderId);

    /**
     * R4/QA P1-1：订单预付款承诺合计（PENDING+SETTLED，在途计入）——预付款累计上限、
     * 尾款扣减与结清校验统一口径，堵住"在途不可见时超额创建/结清"窗口。type=2=PREPAYMENT。
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM settlement"
            + " WHERE deleted = 0 AND order_id = #{orderId} AND status IN (0, 1) AND type = 2")
    BigDecimal sumPrepaymentCommitted(@Param("orderId") Long orderId);
}
