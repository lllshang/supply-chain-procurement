package com.dzgylxt.vo.catalog;

import lombok.Data;

import java.io.Serializable;

/**
 * 规格配置新增/编辑请求。
 */
@Data
public class SpecOptionSaveReqVO implements Serializable {

    private String specName;
    private String specValue;
    /** 0=有效 1=无效 */
    private Integer status;
}
