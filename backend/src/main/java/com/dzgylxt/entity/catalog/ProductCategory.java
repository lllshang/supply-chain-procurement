package com.dzgylxt.entity.catalog;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 商品三级品类（L1/L2/L3）。
 *
 * <p>{@code spu.category_id} 仅可引用叶子节点（level=3 且无子节点）。被 SPU 引用的节点
 * 不可物理删除，仅可置 {@code status=1}（无效）。{@code tree_path} 冗余祖先路径便于子树查询。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("product_category")
public class ProductCategory extends BaseEntity implements Serializable {

    /** 父节点ID，0=根 */
    private Long parentId;
    /** 层级：1/2/3 */
    private Integer level;
    /** 品类编码（有效期内唯一） */
    private String code;
    /** 品类名称 */
    private String name;
    /** 祖先路径，如 /1/3/7 */
    private String treePath;
    /** 0=有效，1=无效 */
    private Integer status;
}
