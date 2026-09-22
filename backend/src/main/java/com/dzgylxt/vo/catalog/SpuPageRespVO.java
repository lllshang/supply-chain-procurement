package com.dzgylxt.vo.catalog;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * SPU 分页响应。
 */
@Data
public class SpuPageRespVO implements Serializable {

    private Long id;
    private String spuCode;
    private String name;
    private Long categoryId;
    private String categoryName;
    private String baseUnit;
    private String imageFileKey;
    /** 0=正常 1=停用 */
    private Integer status;
    private LocalDateTime updatedAt;
}
