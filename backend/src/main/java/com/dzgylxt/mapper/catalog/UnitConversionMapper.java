package com.dzgylxt.mapper.catalog;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.catalog.UnitConversion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** 单位换算 Mapper。 */
@Mapper
public interface UnitConversionMapper extends BaseMapper<UnitConversion> {

    /**
     * 取当前生效版本（effective_from 为空或 <= now 的最大 version）。
     *
     * <p>基准时间由应用侧传入（{@code now}）而非使用 DB 的 {@code NOW()}：写入 {@code effective_from}
     * 用的是 JVM 本地时钟（{@code LocalDateTime.now()}），若读取时用 DB 自身时钟，在"DB 时区 ≠ 应用时区"
     * （如容器 UTC vs 应用 Asia/Shanghai）场景下会出现跨时区比较导致恒不命中。统一采用应用时钟可消除该差异。
     *
     * @param skuId    SKU ID
     * @param fromUnit 原单位
     * @param now      基准时间（应用时钟）
     * @return 当前生效的最大版本记录，无则返回 {@code null}
     */
    @Select("SELECT * FROM unit_conversion WHERE deleted = 0 AND sku_id = #{skuId}"
            + " AND from_unit = #{fromUnit}"
            + " AND (effective_from IS NULL OR effective_from <= #{now})"
            + " ORDER BY version DESC, effective_from DESC LIMIT 1")
    UnitConversion selectCurrentEffective(@Param("skuId") Long skuId,
                                          @Param("fromUnit") String fromUnit,
                                          @Param("now") LocalDateTime now);

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
