package com.dzgylxt.mapper.contract;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.contract.ContractType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 合同类型字典 Mapper（R3a）。 */
@Mapper
public interface ContractTypeMapper extends BaseMapper<ContractType> {

    /** 引用计数：contract.type_id 指向本类型的存量合同数（被引用不可删，PRD L840）。 */
    @Select("SELECT COUNT(*) FROM contract WHERE type_id = #{typeId} AND deleted = 0")
    long countContractRef(@Param("typeId") Long typeId);
}
