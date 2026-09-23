package com.dzgylxt.vo.catalog;

import com.dzgylxt.enums.ProductStatus;
import lombok.Data;

import java.io.Serializable;

/**
 * SPU 分页查询请求（筛选：品类/状态/关键字）。
 */
@Data
public class SpuPageReqVO implements Serializable {

    private Long categoryId;
    private ProductStatus status;
    /** 关键字（匹配 spuCode / name） */
    private String keyword;
    private Long current = 1L;
    private Long size = 10L;
}
