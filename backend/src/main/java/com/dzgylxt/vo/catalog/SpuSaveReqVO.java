package com.dzgylxt.vo.catalog;

import com.dzgylxt.enums.ItemType;
import com.dzgylxt.enums.ProductStatus;
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
    /** 采购项类型：物料类/服务类（缺省物料类） */
    private ItemType itemType;
    /** 规格包装类型：0=普通 1=固定混色箱（缺省普通） */
    private Integer packType;
    private String spec;
    /** 基本单位（引用 unit.code） */
    private String baseUnit;
    private String imageFileKey;
    private String description;
    private String remark;
    /** 0=正常 1=停用 */
    private ProductStatus status;
}
