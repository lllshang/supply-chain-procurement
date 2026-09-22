package com.dzgylxt.mapper.catalog;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.catalog.SpecOption;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 规格配置 Mapper。 */
@Mapper
public interface SpecOptionMapper extends BaseMapper<SpecOption> {

    /** (specName, specValue) 在有效期内是否已存在（可排除自身 id）。 */
    @Select("<script>SELECT COUNT(1) FROM spec_option WHERE deleted = 0"
            + " AND spec_name = #{specName} AND spec_value = #{specValue}"
            + "<if test='excludeId != null'> AND id &lt;&gt; #{excludeId}</if></script>")
    boolean existsNameValue(@Param("specName") String specName,
                            @Param("specValue") String specValue,
                            @Param("excludeId") Long excludeId);
}
