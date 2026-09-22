package com.dzgylxt.entity.catalog;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 供应商三级分类（L1/L2/L3）。
 *
 * <p>与 {@link ProductCategory} 独立两套：商品品类=买什么，供应商分类=供应商行业属性。
 * 结构完全对齐，便于复用树组件。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("supplier_category")
public class SupplierCategory extends BaseEntity implements Serializable {

    /** 父节点ID，0=根 */
    private Long parentId;
    /** 层级：1/2/3 */
    private Integer level;
    /** 分类编码（有效期内唯一） */
    private String code;
    /** 分类名称 */
    private String name;
    /** 祖先路径，如 /1/3/7 */
    private String treePath;
    /** 0=有效，1=无效 */
    private Integer status;
}
