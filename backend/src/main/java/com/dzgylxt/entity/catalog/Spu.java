package com.dzgylxt.entity.catalog;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 商品 SPU（商品定义：名称/分类/规格）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("spu")
public class Spu extends BaseEntity implements Serializable {

    private String spuCode;
    private String name;
    private Long categoryId;
    private String spec;
    private String baseUnit;
    /** 0=正常，1=停用 */
    private Integer status;
    /** 主图 file_key（引用 file_meta.file_key） */
    private String imageFileKey;
    /** 商品简介 */
    private String description;
    private String remark;
}
