package com.dzgylxt.mapper.catalog;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.catalog.PriceRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 价格规则 Mapper。 */
@Mapper
public interface PriceRuleMapper extends BaseMapper<PriceRule> {

    /**
     * 查某引用对象命中的有效规则（SPU 级 + 其品类级），供价格校验。
     *
     * <p>{@code ref_type=1} 命中 {@code spuId}；{@code ref_type=2} 命中 {@code categoryId}。</p>
     */
    @Select("<script>SELECT * FROM price_rule WHERE deleted = 0 AND status = 0 AND ("
            + "(ref_type = 1<if test='spuId != null'> AND ref_id = #{spuId}</if>)"
            + " OR (ref_type = 2<if test='categoryId != null'> AND ref_id = #{categoryId}</if>)"
            + ")</script>")
    List<PriceRule> selectEffectiveByRef(@Param("spuId") Long spuId,
                                         @Param("categoryId") Long categoryId);
}
