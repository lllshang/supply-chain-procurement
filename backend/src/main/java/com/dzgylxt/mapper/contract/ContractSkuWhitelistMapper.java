package com.dzgylxt.mapper.contract;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.contract.ContractSkuWhitelist;
import org.apache.ibatis.annotations.Mapper;

/** 合同 SKU 白名单 Mapper（D15 方案 A 兜底）。 */
@Mapper
public interface ContractSkuWhitelistMapper extends BaseMapper<ContractSkuWhitelist> {
}
