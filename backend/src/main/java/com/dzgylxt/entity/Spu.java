package com.dzgylxt.entity;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
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
    private String spec;
    private String baseUnit;
    /** 0=正常，1=停用 */
    private Integer status;
    private String remark;
}
