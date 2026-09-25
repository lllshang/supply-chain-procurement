package com.dzgylxt.entity.catalog;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.common.BaseEntity;
import com.dzgylxt.enums.CatalogStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 规格维度可选值（"规格名-规格值"对）。
 *
 * <p>同一 {@code specName} 下多行形成可选值列表（如 颜色→红、颜色→绿）；
 * {@code (specName, specValue)} 在有效期内唯一。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("spec_option")
public class SpecOption extends BaseEntity implements Serializable {

    /** 规格名，如 颜色/尺寸 */
    private String specName;
    /** 规格值，如 红/S */
    private String specValue;
    /** 0=有效，1=无效 */
    private CatalogStatus status;
}
