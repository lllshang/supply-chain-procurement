package com.dzgylxt.vo.order;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 到货单创建请求（设计 §2.7：按订单展开明细，应收 = 订单明细未入库余量）。 */
@Data
public class ArrivalCreateReqVO implements Serializable {

    private Long orderId;
    /** 到货凭证附件（file_meta.file_key） */
    private String voucherFileKey;
    private String remark;
    /** 实收明细（缺省 = 按订单明细全额实收）；数量一律基本单位口径 */
    private List<ItemActual> items = new ArrayList<>();

    @Data
    public static class ItemActual implements Serializable {
        private Long orderItemId;
        /** 实收数量（基本单位；计重 SKU=实到重量） */
        private BigDecimal qtyActual;
        private String remark;
        /** P4 R3b 计重：实到重量（基本单位口径，valuation_type=1 时录入） */
        private BigDecimal actualWeight;
        /** P4 R3b 计重：合格量（硬校验：合格量 ≤ 实到重量，PRD L811） */
        private BigDecimal qualifiedQty;
    }
}
