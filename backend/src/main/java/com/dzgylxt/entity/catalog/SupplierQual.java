package com.dzgylxt.entity.catalog;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 供应商资质（H5 提交 / 平台审核 / 驳回重提）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("supplier_qual")
public class SupplierQual extends BaseEntity implements Serializable {

    private Long supplierId;
    private String type;
    private String fileKey;
    private LocalDateTime expireAt;
    /** 0=待审，1=通过，2=驳回 */
    private Integer status;
}
