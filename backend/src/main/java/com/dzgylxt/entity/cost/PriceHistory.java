package com.dzgylxt.entity.cost;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.common.BaseEntity;
import com.dzgylxt.enums.PriceAuditStatus;
import com.dzgylxt.enums.PriceSource;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 价格库（P3 设计 §1.2.2；事后成交价沉淀，与 P1 price_rule 事前限价两套分工不合一，Q5）。
 *
 * <p>沉淀埋点（自动写入 {@code audit_status=APPROVED} 的通过价）：报价采纳、
 * 定标审批通过、订单生成；异常价（超 price_rule 限价或低于历史均价 20%）
 * 写 {@code PENDING} 待审。P2 比价"历史比价"取数切换/兜底为本表（仅取通过价）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("price_history")
public class PriceHistory extends BaseEntity implements Serializable {
    private Long skuId;
    /** 供应商（手工/品类级价可空） */
    private Long supplierId;
    /** 单价（基本单位口径） */
    private BigDecimal price;
    /** 来源：报价/定标/订单/手工 */
    private PriceSource source;
    /** 来源单据类型（QUOTATION/AWARD/ORDER） */
    private String bizType;
    /** 来源单据ID */
    private Long bizId;
    /** 生效日期（默认来源单据日期） */
    private LocalDate effectiveDate;
    /** 审核状态：待审/通过/驳回 */
    private PriceAuditStatus auditStatus;
    private Long auditBy;
    private LocalDateTime auditAt;
    private String remark;
}
