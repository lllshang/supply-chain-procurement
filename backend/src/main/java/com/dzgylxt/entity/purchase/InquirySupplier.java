package com.dzgylxt.entity.purchase;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 询价供应商范围（设计 §1.2.2；发布时逐家准入校验 + 落 admission_snapshot 审计快照）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("inquiry_supplier")
public class InquirySupplier extends BaseEntity implements Serializable {

    /** 询价单ID */
    private Long inquiryId;
    /** 供应商ID */
    private Long supplierId;
    /** 是否邀请：0/1 */
    private Integer invited;
    /** 是否已报价：0/1 */
    private Integer quoted;
    /** 准入快照 JSON：{qualified,reasons[],coopStatus,isBlacklist,qualValidity}（发布时落，审计用） */
    private String admissionSnapshot;
    private String remark;
}
