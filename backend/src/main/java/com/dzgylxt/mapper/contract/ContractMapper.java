package com.dzgylxt.mapper.contract;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.contract.Contract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/** 合同 Mapper（含下单额度扣减的行锁查询与条件扣减）。 */
@Mapper
public interface ContractMapper extends BaseMapper<Contract> {

    /**
     * 按合同 ID 锁行（FOR UPDATE，设计 §4.2 校验事务第一把行锁）。
     *
     * <p>锁序约定：全局统一 {@code contract → purchase_apply_item}，
     * 本方法必须先于 PurchaseApplyItemMapper 的 FOR UPDATE 调用。</p>
     */
    @Select("SELECT * FROM contract WHERE id = #{id} FOR UPDATE")
    Contract selectForUpdate(@Param("id") Long id);

    /**
     * 条件扣减可用额度（乐观版本兜底，主防=行锁）。
     *
     * <p>SQL 条件 {@code version=? AND available_amount >= delta} 双重保护：
     * 行锁遗漏的极端场景下不超扣，返回 0 行表示冲突/不足，由调用方重试或抛业务异常。</p>
     */
    @Update("UPDATE contract SET available_amount = available_amount - #{delta},"
            + " version = version + 1"
            + " WHERE id = #{id} AND version = #{version} AND available_amount >= #{delta}")
    int deductAvailable(@Param("id") Long id,
                        @Param("delta") BigDecimal delta,
                        @Param("version") Integer version);

    /** 回冲可用额度（取消/变更差额，乐观版本兜底）。 */
    @Update("UPDATE contract SET available_amount = available_amount + #{delta},"
            + " version = version + 1"
            + " WHERE id = #{id} AND version = #{version}")
    int releaseAvailable(@Param("id") Long id,
                         @Param("delta") BigDecimal delta,
                         @Param("version") Integer version);
}
