package com.dzgylxt.vo.supplier;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 供应商检索响应。
 */
@Data
public class SupplierPageRespVO implements Serializable {

    private Long id;
    private String name;
    private String creditCode;
    private String level;
    private Long supplierCategoryId;
    private String categoryName;
    /** 合作状态：0=正常 1=停用 2=冻结 */
    private Integer coopStatus;
    /** 黑名单：0=否 1=是 */
    private Integer isBlacklist;
    /** 来源：0=平台录入 1=H5提交 2=导入 */
    private Integer source;
    /** 资质状态：0=审核中 1=通过 2=驳回 */
    private Integer status;
    private LocalDateTime updatedAt;
}
