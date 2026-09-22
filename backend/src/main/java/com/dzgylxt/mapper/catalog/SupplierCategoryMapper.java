package com.dzgylxt.mapper.catalog;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.catalog.SupplierCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 供应商三级分类 Mapper。 */
@Mapper
public interface SupplierCategoryMapper extends BaseMapper<SupplierCategory> {

    /** 编码在有效期内是否已存在（可排除自身 id）。 */
    @Select("<script>SELECT COUNT(1) FROM supplier_category WHERE deleted = 0 AND code = #{code}"
            + "<if test='excludeId != null'> AND id &lt;&gt; #{excludeId}</if></script>")
    boolean existsByCode(@Param("code") String code, @Param("excludeId") Long excludeId);

    /** 是否存在子节点。 */
    @Select("SELECT COUNT(1) FROM supplier_category WHERE deleted = 0 AND parent_id = #{parentId}")
    boolean existsChildren(@Param("parentId") Long parentId);
}
