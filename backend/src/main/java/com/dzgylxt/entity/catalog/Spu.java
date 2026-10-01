package com.dzgylxt.entity.catalog;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.ItemType;
import com.dzgylxt.enums.ProductStatus;
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
    /** 采购项类型：0=物料类 1=服务类（PRD PM-04/BR-22/BR-25；服务类不进库存） */
    private ItemType itemType;
    /** 规格包装类型：0=普通 1=固定混色箱（原型规格类型筛选用） */
    private Integer packType;
    private String spec;
    private String baseUnit;
    /** 0=正常，1=停用 */
    private ProductStatus status;
    /** 主图 file_key（引用 file_meta.file_key） */
    private String imageFileKey;
    /** 商品简介 */
    private String description;
    private String remark;
}
