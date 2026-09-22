package com.dzgylxt.mapper.purchase;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.purchase.FrequentPurchase;
import org.apache.ibatis.annotations.Mapper;

/** 部门常购清单 Mapper。 */
@Mapper
public interface FrequentPurchaseMapper extends BaseMapper<FrequentPurchase> {
}
