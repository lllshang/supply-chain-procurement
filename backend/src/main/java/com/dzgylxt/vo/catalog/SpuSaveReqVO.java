package com.dzgylxt.vo.catalog;

import lombok.Data;

import java.io.Serializable;

/**
 * SPU 新增/编辑请求。
 */
@Data
public class SpuSaveReqVO implements Serializable {

    private String spuCode;
    private String name;
    /** 三级品类（必须为叶子节点） */
    private Long categoryId;
    private String spec;
    /** 基本单位（引用 unit.code） */
    private String baseUnit;
    private String imageFileKey;
    private String description;
    private String remark;
    /** 0=正常 1=停用 */
    private Integer status;
}
