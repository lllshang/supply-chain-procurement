package com.dzgylxt.mapper.cost;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.cost.PriceHistory;
import org.apache.ibatis.annotations.Mapper;

/** 价格库 Mapper（P3 设计 §1.2.2；比价历史兜底/切换数据源）。 */
@Mapper
public interface PriceHistoryMapper extends BaseMapper<PriceHistory> {
}
