package com.dzgylxt.entity.catalog;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.common.BaseEntity;
import com.dzgylxt.enums.CatalogStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 计量单位字典。
 *
 * <p>{@code sku.base_unit} / {@code sku.purchase_unit} / {@code supplier_sku.package_unit} /
 * {@code unit_conversion.from_unit} / {@code to_unit} 均以 {@code unit.code} 作为字符串关联键，
 * 与既有 String 类型字段保持一致。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("unit")
public class Unit extends BaseEntity implements Serializable {

    /** 单位编码（有效期内唯一），如 PCS/BOX/KG */
    private String code;
    /** 单位名称，如 个/箱/千克 */
    private String name;
    /** 0=有效，1=无效 */
    private CatalogStatus status;
}
