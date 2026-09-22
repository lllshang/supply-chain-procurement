package com.dzgylxt.mapper.catalog;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.catalog.SupplierSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 供应商-商品绑定 Mapper。 */
@Mapper
public interface SupplierSkuMapper extends BaseMapper<SupplierSku> {

    /** 同一 (supplierId, skuId) 是否已有有效绑定（可排除自身 id）。 */
    @Select("<script>SELECT COUNT(1) FROM supplier_sku WHERE deleted = 0"
            + " AND supplier_id = #{supplierId} AND sku_id = #{skuId}"
            + "<if test='excludeId != null'> AND id &lt;&gt; #{excludeId}</if></script>")
    boolean existsBind(@Param("supplierId") Long supplierId,
                       @Param("skuId") Long skuId,
                       @Param("excludeId") Long excludeId);
}
