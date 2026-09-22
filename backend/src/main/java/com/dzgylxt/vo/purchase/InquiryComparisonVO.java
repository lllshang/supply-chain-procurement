package com.dzgylxt.vo.purchase;

import com.dzgylxt.entity.purchase.Quotation;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 比价视图（设计 §2.2 comparison：按 SKU 汇总最低/最高/均价 + 历史价近 5 次；B2 综合评分法不做自动算分）。 */
@Data
public class InquiryComparisonVO implements Serializable {

    private Long inquiryId;
    private String inquiryNo;
    private List<SkuComparison> items = new ArrayList<>();

    @Data
    public static class SkuComparison implements Serializable {
        private Long skuId;
        private String skuCode;
        /** 最低价（基本单位口径） */
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private BigDecimal avgPrice;
        /** 本询价范围内有效报价 */
        private List<Quotation> quotations = new ArrayList<>();
        /** 历史价（近 5 次，可配置） */
        private List<PriceHistory> history = new ArrayList<>();
    }

    @Data
    public static class PriceHistory implements Serializable {
        private Long skuId;
        private Long supplierId;
        private BigDecimal price;
        private String source;
        private String time;
    }
}
