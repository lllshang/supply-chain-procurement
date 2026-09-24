package com.dzgylxt.entity.order;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 服务验收扣款明细（P3c-A5 / PRD L812"一条或多条扣款明细；扣款项目取服务考核指标"；
 * L879-880；BR-15 L1090）。
 *
 * <p>{@code service_assess.deduct_amount} 保留为汇总冗余（= Σ 明细，写入时同事务回填），
 * 结算取数（R-STL-02）口径不变、粒度变细。应付非负校验：Σ 明细 ≤ 应付基数，超出拒绝。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("service_deduction_item")
public class ServiceDeductionItem extends BaseEntity implements Serializable {

    /** 所属服务考核单（逻辑外键 service_assess.id） */
    private Long assessId;
    /** 扣款项目（取服务考核指标，L812） */
    private String itemName;
    /** 扣款金额 */
    private BigDecimal amount;
    /** 扣款原因 */
    private String reason;
}
