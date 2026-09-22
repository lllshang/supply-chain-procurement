package com.dzgylxt.mapper.catalog;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.catalog.Supplier;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 供应商 Mapper。 */
@Mapper
public interface SupplierMapper extends BaseMapper<Supplier> {

    /** 统一社会信用代码在有效期内是否已存在（可排除自身 id）。 */
    @Select("<script>SELECT COUNT(1) FROM supplier WHERE deleted = 0 AND credit_code = #{creditCode}"
            + "<if test='excludeId != null'> AND id &lt;&gt; #{excludeId}</if></script>")
    boolean existsByCreditCode(@Param("creditCode") String creditCode, @Param("excludeId") Long excludeId);
}
