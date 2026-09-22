package com.dzgylxt.vo.supplier;

import lombok.Data;

import java.io.Serializable;

/**
 * 供应商检索请求（名称/分类/等级/合作状态/黑名单组合）。
 */
@Data
public class SupplierPageReqVO implements Serializable {

    private String name;
    private Long categoryId;
    private String level;
    /** 合作状态：0=正常 1=停用 2=冻结 */
    private Integer coopStatus;
    /** 黑名单：0=否 1=是 */
    private Integer blacklist;
    private Long current = 1L;
    private Long size = 10L;
}
