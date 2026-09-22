package com.dzgylxt.mapper.contract;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.contract.Contract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 合同 Mapper（含下单额度扣减的行锁查询）。 */
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
}
