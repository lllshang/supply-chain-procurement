package com.dzgylxt.mapper.contract;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.contract.ContractPriceItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 合同价格清单 Mapper（P3c-A1：下单第四重校验取数）。 */
@Mapper
public interface ContractPriceItemMapper extends BaseMapper<ContractPriceItem> {

    /** 合同的价格清单（按 id 升序；空列表 = 该合同免价格校验）。 */
    @Select("SELECT * FROM contract_price_item"
            + " WHERE deleted = 0 AND contract_id = #{contractId} ORDER BY id ASC")
    List<ContractPriceItem> selectByContract(@Param("contractId") Long contractId);

    /** 合同+SKU 命中行（同 SKU 多行时取 id 最小者；价格一致性由调用方校验）。 */
    @Select("SELECT * FROM contract_price_item"
            + " WHERE deleted = 0 AND contract_id = #{contractId} AND sku_id = #{skuId}"
            + " ORDER BY id ASC")
    List<ContractPriceItem> selectByContractAndSku(@Param("contractId") Long contractId,
                                                   @Param("skuId") Long skuId);
}
