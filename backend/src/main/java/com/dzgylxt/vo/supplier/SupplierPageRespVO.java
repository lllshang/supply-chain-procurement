package com.dzgylxt.vo.supplier;

import com.dzgylxt.enums.CoopStatus;
import com.dzgylxt.enums.BlacklistFlag;
import com.dzgylxt.enums.SupplierSource;
import com.dzgylxt.enums.SupplierStatus;
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
    /** 合作状态（#33 name 契约：吐 name） */
    private CoopStatus coopStatus;
    /** 黑名单（#33 name 契约） */
    private BlacklistFlag isBlacklist;
    /** 来源（#33 name 契约） */
    private SupplierSource source;
    /** 资质状态（#33 name 契约） */
    private SupplierStatus status;
    private LocalDateTime updatedAt;
}
