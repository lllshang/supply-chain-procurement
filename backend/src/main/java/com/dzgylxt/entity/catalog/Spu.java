package com.dzgylxt.entity.catalog;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.ItemType;
import com.dzgylxt.enums.ProductStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

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
    /** 税率%（目录域产品级，合法域 0–13，与采购域 P3c-A2 含税口径对齐；可空） */
    private BigDecimal taxRate;

    // ===== 编辑对话框全量对齐原型（单/多规格 + 固定混色箱） =====
    /** 产品条码（单规格/混色箱模式录入；多规格由子 SKU 承载） */
    private String barcode;
    /** 计量方式：piece=计件 weight=计重（非服务类） */
    private String measurementType;
    /** 采购单位（引用 unit.code；默认同基本单位） */
    private String purchaseUnit;
    /** 规格模式：single=单规格 multiple=多规格 mixed=固定混色箱 */
    private String specificationMode;
    /** 单位换算系数：1 采购单位 = N 基本单位（单规格/混色箱模式） */
    private Integer unitConversionFactor;
    /** 标准价（SPU 级，单规格/混色箱模式；元/采购单位） */
    private BigDecimal standardPrice;
    /** 参考价（SPU 级，单规格/混色箱模式；元/采购单位） */
    private BigDecimal referencePrice;
    /** 固定混色箱子件 JSON：[{name,skuCode,specification,quantity,baseUnit}] */
    private String mixedPackComponents;
    /** 多规格属性定义 JSON：[{id,name,values[]}] */
    private String specAttributes;
}
