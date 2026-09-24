package com.dzgylxt.entity.purchase;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.QuotationStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 报价（线下 Excel 导入）。
 *
 * <p>P2 §1.3.4：批次维度管理——新批次导入后同询价旧批次 {@code invalid=1}（不物理删）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("quotation")
public class Quotation extends BaseEntity implements Serializable {

    private Long inquiryId;
    /** 报价批次 BJ-{inquiry_no}-{seq2}（P2 新增） */
    private String batchNo;
    private Long supplierId;
    private Long skuId;
    /** 报价单位（unit.code，P2 新增） */
    private String purchaseUnit;
    /** 基本单位数量（导入单位换算所得，P2 新增） */
    private BigDecimal qtyInBaseUnit;
    /** 原始报价附件（file_meta.file_key，P2 新增） */
    private String fileKey;
    private BigDecimal price;
    /** @deprecated P2 起弃用停写（仅历史展示），换算改用 purchase_unit + qty_in_base_unit 结构化口径。 */
    @Deprecated
    private String convSnapshot;
    private QuotationStatus status;
    /** 0=有效 1=已失效（新批次导入后旧批次置 1，P2 新增） */
    private Integer invalid;
    /** P3c-A2：税率 %（PRD L697/L935；含税口径，合法域 0–13） */
    private BigDecimal taxRate;
    /** P3c-A2：运费（PRD L717 比价维度之一） */
    private BigDecimal freight;
    /** P3c-A2：承诺交期天数（PRD L709 供应商填写） */
    private Integer deliveryDays;
}
