package com.dzgylxt.mapper.budget;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.budget.BudgetOccupyLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 预算占用/释放/核销流水 Mapper（P3 设计 §1.2.1）。
 *
 * <p>对账恒等式（按 budget_line_id）：{@code Σ(占用) − Σ(释放) == used_amount}；
 * 核销（WRITE_OFF）与调整（ADJUST）不参与该恒等式。</p>
 *
 * <p><b>P2b-9 口径定稿（写侧一律正数，查询侧取负）</b>：OCCUPY/RELEASE/WRITE_OFF
 * 的 amount <b>统一存正数</b>（动作语义由 action 表达，金额=本次动作绝对值，
 * balance 前后快照承载方向）；查询侧对 action∈(RELEASE, WRITE_OFF) 取负。
 * ADJUST 存带符号 delta、不参与恒等式。
 * <b>禁止写侧再落负值</b>——历史缺陷：RELEASE 落负 + 查询取负 = 双重取反，
 * 有释放历史的 bizId 占用总额翻倍（P2b 第 2 轮 QA 三实锤）。
 * 存量负值行由 {@code scripts/sql/p2b_migration.sql} 幂等翻正。</p>
 */
@Mapper
public interface BudgetOccupyLogMapper extends BaseMapper<BudgetOccupyLog> {

    /** 某预算行上某业务单据的占用余额 = Σ(占用) − Σ(释放) − Σ(核销)（按行锁定语义）。 */
    @Select("SELECT COALESCE(SUM(CASE WHEN action = 0 THEN amount"
            + " WHEN action IN (1, 2) THEN -amount ELSE 0 END), 0)"
            + " FROM budget_occupy_log WHERE deleted = 0"
            + " AND budget_line_id = #{budgetLineId} AND biz_type = #{bizTypeCode} AND biz_id = #{bizId}")
    BigDecimal sumBizOccupied(@Param("budgetLineId") Long budgetLineId,
                              @Param("bizTypeCode") Integer bizTypeCode,
                              @Param("bizId") Long bizId);

    /** 某业务单据在某行上发生过动作的行数（定位转移/核销涉及的行，调用方按 id 排序加锁）。 */
    @Select("SELECT COUNT(DISTINCT budget_line_id) FROM budget_occupy_log"
            + " WHERE deleted = 0 AND biz_type = #{bizTypeCode} AND biz_id = #{bizId}")
    int countLinesByBiz(@Param("bizTypeCode") Integer bizTypeCode, @Param("bizId") Long bizId);

    /** 某业务单据发生过动作的预算行ID集合（调用方按 id 升序加锁遍历）。 */
    @Select("SELECT DISTINCT budget_line_id FROM budget_occupy_log"
            + " WHERE deleted = 0 AND biz_type = #{bizTypeCode} AND biz_id = #{bizId}"
            + " ORDER BY budget_line_id ASC")
    java.util.List<Long> selectLineIdsByBiz(@Param("bizTypeCode") Integer bizTypeCode,
                                            @Param("bizId") Long bizId);

    /** 每日对账：某行的 Σlog(占用−释放)（对齐 budget_line.used_amount 校验）。 */
    @Select("SELECT COALESCE(SUM(CASE WHEN action = 0 THEN amount"
            + " WHEN action = 1 THEN -amount ELSE 0 END), 0)"
            + " FROM budget_occupy_log WHERE deleted = 0 AND budget_line_id = #{budgetLineId}")
    BigDecimal sumNetOccupiedByLine(@Param("budgetLineId") Long budgetLineId);
}
