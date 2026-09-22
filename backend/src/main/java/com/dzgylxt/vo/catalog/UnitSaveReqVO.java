package com.dzgylxt.vo.catalog;

import lombok.Data;

import java.io.Serializable;

/**
 * 计量单位新增/编辑请求。
 */
@Data
public class UnitSaveReqVO implements Serializable {

    private String code;
    private String name;
    /** 0=有效 1=无效 */
    private Integer status;
}
