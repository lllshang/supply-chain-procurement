package com.dzgylxt.mapper.budget;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.budget.BudgetLine;
import org.apache.ibatis.annotations.Mapper;

/** 预算明细 Mapper。 */
@Mapper
public interface BudgetLineMapper extends BaseMapper<BudgetLine> {
}
