package com.dzgylxt.entity.catalog;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.QualStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 供应商资质（H5 提交 / 平台审核 / 驳回重提）。
 *
 * <p>状态机：待审(0) → 通过(1)；待审(0) → 驳回(2) → 待审(0)（修改重提）。驳回必填
 * {@link #rejectReason}，审核落 {@link #reviewedBy}/{@link #reviewedAt}。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("supplier_qual")
public class SupplierQual extends BaseEntity implements Serializable {

    private Long supplierId;
    private String type;
    /** 资质名称 */
    private String qualName;
    private String fileKey;
    private LocalDateTime expireAt;
    /** 0=待审，1=通过，2=驳回 */
    private QualStatus status;
    /** 驳回原因（驳回时必填） */
    private String rejectReason;
    /** 审核人ID */
    private Long reviewedBy;
    /** 审核时间 */
    private LocalDateTime reviewedAt;
}
