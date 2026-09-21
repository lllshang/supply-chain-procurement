package com.dzgylxt.entity;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.QuotationStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 报价（线下 Excel 导入）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("quotation")
public class Quotation extends BaseEntity implements Serializable {

    private Long inquiryId;
    private Long supplierId;
    private Long skuId;
    private BigDecimal price;
    private String convSnapshot;
    private QuotationStatus status;
}
