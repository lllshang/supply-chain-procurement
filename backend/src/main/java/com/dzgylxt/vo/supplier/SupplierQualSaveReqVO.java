package com.dzgylxt.vo.supplier;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 资质录入/重提请求。
 */
@Data
public class SupplierQualSaveReqVO implements Serializable {

    private Long supplierId;
    /** 资质类型 */
    private String type;
    /** 资质名称 */
    private String qualName;
    /** 附件 file_key */
    private String fileKey;
    private LocalDateTime expireAt;
}
