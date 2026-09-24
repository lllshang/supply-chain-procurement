package com.dzgylxt.vo.settlement;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 结算草稿带出 VO（P3 设计 §2.1 双入口：订单 / 到货）。
 *
 * <p>前端表单预填：可结余量、建议金额（服务单自动扣考核扣款）、合同累计结算。</p>
 */
@Data
public class SettlementDraftVO implements Serializable {
    private Long orderId;
    private String orderNo;
    private Long supplierId;
    private Long contractId;
    /** 订单类型（MATERIAL/SERVICE，决定扣款取数） */
    private String orderType;
    /** 订单应结总额（Σ 明细 price × qty_base） */
    private BigDecimal orderAmount;
    /** 已入库合格累计（Σ arrival_item.qty_stored） */
    private BigDecimal storedQtyBase;
    /** 已结算数量累计（历史 SETTLED/PENDING 结算单） */
    private BigDecimal settledQtyBase;
    /** 已结算金额累计（历史 SETTLED） */
    private BigDecimal settledAmount;
    /** 可结余量 = storedQtyBase − settledQtyBase（待结算历史单计入占用） */
    private BigDecimal remainQtyBase;
    /** 服务考核扣款合计（物料单=0） */
    private BigDecimal assessDeduct;
    /** R4：本订单已付预付款合计（SETTLED 预付款结算；预付款/尾款表单展示与累计校验用） */
    private BigDecimal prepaidPaid;
    /** 建议结算金额：物料=remainQty×均价；服务=orderAmount×(remainQty/stored)−扣款 */
    private BigDecimal suggestAmount;
    /** 到货单入口：本单已入库量（按 arrival 汇总） */
    private BigDecimal arrivalStoredQty;
    /** 明细带出（SKU/数量/单价） */
    private List<DraftItem> items = new ArrayList<>();

    /** 草稿明细行。 */
    @Data
    public static class DraftItem implements Serializable {
        private Long orderItemId;
        private Long skuId;
        /** 基本单位数量 */
        private BigDecimal qtyBase;
        /** 已入库量 */
        private BigDecimal qtyStored;
        /** 单价（基本单位口径，#27 契约） */
        private BigDecimal price;
    }
}
