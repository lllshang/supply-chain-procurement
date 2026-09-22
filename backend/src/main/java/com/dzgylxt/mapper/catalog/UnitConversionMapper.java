package com.dzgylxt.mapper.catalog;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.catalog.UnitConversion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 单位换算 Mapper。 */
@Mapper
public interface UnitConversionMapper extends BaseMapper<UnitConversion> {

    /** 取当前生效版本（effective_from 为空或 <= now 的最大 version）。 */
    @Select("SELECT * FROM unit_conversion WHERE deleted = 0 AND sku_id = #{skuId}"
            + " AND from_unit = #{fromUnit}"
            + " AND (effective_from IS NULL OR effective_from <= NOW())"
            + " ORDER BY version DESC, effective_from DESC LIMIT 1")
    UnitConversion selectCurrentEffective(@Param("skuId") Long skuId, @Param("fromUnit") String fromUnit);

    /** 取某 SKU 的换算历史（版本倒序）。 */
    @Select("SELECT * FROM unit_conversion WHERE deleted = 0 AND sku_id = #{skuId}"
            + " ORDER BY from_unit ASC, version DESC, effective_from DESC")
    List<UnitConversion> selectHistory(@Param("skuId") Long skuId);

    /** 取某 SKU 某 (from,to) 组合的最大 version（用于生成新版本号）。 */
    @Select("SELECT COALESCE(MAX(version), 0) FROM unit_conversion WHERE deleted = 0"
            + " AND sku_id = #{skuId} AND from_unit = #{fromUnit} AND to_unit = #{toUnit}")
    Integer maxVersion(@Param("skuId") Long skuId,
                       @Param("fromUnit") String fromUnit,
                       @Param("toUnit") String toUnit);
}
