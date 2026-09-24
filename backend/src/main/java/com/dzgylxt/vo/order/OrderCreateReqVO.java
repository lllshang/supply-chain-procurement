package com.dzgylxt.vo.order;

import com.dzgylxt.enums.ItemType;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 订单创建请求（设计 §2.6 / §4：三重校验事务）。 */
@Data
public class OrderCreateReqVO implements Serializable {

    /** 合同（三重校验规则①②） */
    private Long contractId;
    /** 来源申请（三重校验规则③） */
    private Long applyId;
    /** 供应商（缺省取合同供应商） */
    private Long supplierId;
    private String remark;
    private List<OrderItemReqVO> items = new ArrayList<>();

    @Data
    public static class OrderItemReqVO implements Serializable {
        /** 申请明细（余量扣减对象） */
        private Long applyItemId;
        /** 行类型（P2b：无申请来源明细必传，MATERIAL/SERVICE；有申请明细时忽略） */
        private ItemType itemType;
        /** 来源定标明细（可选，与 apply_item_id 并存） */
        private Long sourceAwardItem;
        private Long skuId;
        /** 下单数量（采购单位） */
        private BigDecimal qty;
        private String purchaseUnit;
        /** 下单单价（基本单位口径，对齐 award_item.price） */
        private BigDecimal price;
        /** 到货计划日期（P2-T09 逾期扫描依据） */
        private LocalDate planDate;
    }
}
