package com.dzgylxt.vo.catalog;

import com.dzgylxt.enums.ItemType;
import com.dzgylxt.enums.ProductStatus;
import lombok.Data;

import java.io.Serializable;

/**
 * SPU 分页查询请求（筛选：品类/状态/关键字/采购项类型/规格类型）。
 */
@Data
public class SpuPageReqVO implements Serializable {

    private Long categoryId;
    private ProductStatus status;
    /** 关键字（匹配 spuCode / name） */
    private String keyword;
    /** 采购项类型：物料类/服务类（原型筛选面板「采购项类型」） */
    private ItemType itemType;
    /**
     * 规格类型筛选（派生，不落库）：single=单规格 / multiple=多规格 / mixed=固定混色箱。
     * 单/多规格由 SKU 数量推导，固定混色箱由 spu.pack_type 决定。
     */
    private String specMode;
    private Long current = 1L;
    private Long size = 10L;
}
