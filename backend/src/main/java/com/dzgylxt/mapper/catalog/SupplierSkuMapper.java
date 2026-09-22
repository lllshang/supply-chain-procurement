package com.dzgylxt.mapper.catalog;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.catalog.SupplierSku;
import org.apache.ibatis.annotations.Delete;
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

    /**
     * 物理删除某 (supplierId, skuId) 下所有已逻辑删除（deleted=1）的历史行。
     *
     * <p>用于解绑前置清理：唯一约束 {@code uk_sup_sku(supplier_id, sku_id, deleted)} 中
     * {@code deleted} 为常量删除值(1)，同一组合至多允许 1 条已删行；解绑前先清掉旧已删行，
     * 保证「解绑→重绑→再解绑」可反复进行（每对仅保留 1 条已删行）。</p>
     *
     * @return 物理删除的行数
     */
    @Delete("DELETE FROM supplier_sku WHERE supplier_id = #{supplierId}"
            + " AND sku_id = #{skuId} AND deleted = 1")
    int physicalDeleteDeletedByPair(@Param("supplierId") Long supplierId,
                                    @Param("skuId") Long skuId);
}
