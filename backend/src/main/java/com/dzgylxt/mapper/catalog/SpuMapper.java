package com.dzgylxt.mapper.catalog;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dzgylxt.entity.catalog.Spu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 商品 SPU Mapper。 */
@Mapper
public interface SpuMapper extends BaseMapper<Spu> {

    /** SPU 编码在有效期内是否已存在（可排除自身 id）。 */
    @Select("<script>SELECT COUNT(1) FROM spu WHERE deleted = 0 AND spu_code = #{code}"
            + "<if test='excludeId != null'> AND id &lt;&gt; #{excludeId}</if></script>")
    boolean existsByCode(@Param("code") String code, @Param("excludeId") Long excludeId);

    /**
     * 采购最低价同步标准价：将存在历史采购明细（order_item.price 非空）的 SPU 标准价回写为
     * 其所有采购明细中的最低单价。返回被更新的 SPU 行数。
     */
    @Update("""
            UPDATE spu s
            SET standard_price = (
                SELECT MIN(oi.price) FROM order_item oi JOIN sku k ON oi.sku_id = k.id WHERE k.spu_id = s.id
            )
            WHERE s.status = 0
              AND s.deleted = 0
              AND EXISTS (
                SELECT 1 FROM order_item oi2 JOIN sku k2 ON oi2.sku_id = k2.id
                WHERE k2.spu_id = s.id AND oi2.price IS NOT NULL
              )
            """)
    int syncStandardPriceFromLowestOrder();
}
