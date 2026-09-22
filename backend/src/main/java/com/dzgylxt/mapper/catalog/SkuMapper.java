package com.dzgylxt.mapper.catalog;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.catalog.Sku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 商品 SKU Mapper。 */
@Mapper
public interface SkuMapper extends BaseMapper<Sku> {

    /** SKU 编码在有效期内是否已存在（可排除自身 id）。 */
    @Select("<script>SELECT COUNT(1) FROM sku WHERE deleted = 0 AND sku_code = #{skuCode}"
            + "<if test='excludeId != null'> AND id &lt;&gt; #{excludeId}</if></script>")
    boolean existsBySkuCode(@Param("skuCode") String skuCode, @Param("excludeId") Long excludeId);

    /** 条码在有效期内是否已存在（可排除自身 id）。 */
    @Select("<script>SELECT COUNT(1) FROM sku WHERE deleted = 0 AND barcode = #{barcode}"
            + "<if test='excludeId != null'> AND id &lt;&gt; #{excludeId}</if></script>")
    boolean existsByBarcode(@Param("barcode") String barcode, @Param("excludeId") Long excludeId);
}
