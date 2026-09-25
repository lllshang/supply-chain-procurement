package com.dzgylxt.vo.supplier;

import lombok.Data;

import com.dzgylxt.enums.QualStatus;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 资质列表响应（含派生有效期）。
 */
@Data
public class SupplierQualRespVO implements Serializable {

    private Long id;
    private Long supplierId;
    private String type;
    private String qualName;
    private String fileKey;
    private LocalDateTime expireAt;
    /** 0=待审 1=通过 2=驳回 */
    private QualStatus status;
    /** 派生有效期：VALID / EXPIRING / EXPIRED */
    private String validity;
    private String rejectReason;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime updatedAt;
}
