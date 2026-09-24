package com.dzgylxt.mapper.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.order.ServiceDeductionItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 服务扣款明细 Mapper（P3c-A5）。 */
@Mapper
public interface ServiceDeductionItemMapper extends BaseMapper<ServiceDeductionItem> {

    /** 考核单的扣款明细（按 id 升序）。 */
    @Select("SELECT * FROM service_deduction_item"
            + " WHERE deleted = 0 AND assess_id = #{assessId} ORDER BY id ASC")
    List<ServiceDeductionItem> selectByAssess(@Param("assessId") Long assessId);
}
