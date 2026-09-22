package com.dzgylxt.vo.purchase;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 定标保存请求（设计 §2.4：按 SKU 可拆分多供应商）。 */
@Data
public class AwardSaveReqVO implements Serializable {

    /** 来源询价 */
    private Long inquiryId;
    /** 追溯申请（定标→申请上溯） */
    private Long applyId;
    private String remark;
    private List<AwardItemVO> items = new ArrayList<>();

    @Data
    public static class AwardItemVO implements Serializable {
        private Long skuId;
        /** 中标供应商 */
        private Long supplierId;
        /** 定标单价（基本单位口径） */
        private BigDecimal price;
        /** 定标数量（采购单位） */
        private BigDecimal qty;
        private String remark;
    }
}
