package com.dzgylxt.mapper.catalog;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.catalog.Unit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 计量单位 Mapper。 */
@Mapper
public interface UnitMapper extends BaseMapper<Unit> {

    /** 编码在有效期内是否已存在（可排除自身 id）。 */
    @Select("<script>SELECT COUNT(1) FROM unit WHERE deleted = 0 AND code = #{code}"
            + "<if test='excludeId != null'> AND id &lt;&gt; #{excludeId}</if></script>")
    boolean existsByCode(@Param("code") String code, @Param("excludeId") Long excludeId);
}
