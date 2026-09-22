package com.dzgylxt.vo.order;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 订单变更请求（设计 §2.6 Q9：仅 CREATED 可变更，重跑三重校验，免审留痕）。 */
@Data
public class OrderChangeReqVO implements Serializable {

    private String reason;
    private List<ItemChange> items = new ArrayList<>();

    @Data
    public static class ItemChange implements Serializable {
        /** 订单明细 ID */
        private Long orderItemId;
        /** 新数量（采购单位；null = 不变更数量） */
        private BigDecimal newQty;
        /** 新单价（基本单位口径；null = 不变更价格） */
        private BigDecimal newPrice;
    }
}
