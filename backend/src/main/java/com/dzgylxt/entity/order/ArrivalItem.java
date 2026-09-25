package com.dzgylxt.entity.order;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.DiffType;
import com.dzgylxt.enums.HandleStatus;
import com.dzgylxt.enums.HandleType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 到货验收明细（设计 §1.2.5；差异退货补货 / 入库流水，数量一律基本单位口径）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("arrival_item")
public class ArrivalItem extends BaseEntity implements Serializable {

    /** 到货单ID */
    private Long arrivalId;
    /** 订单明细ID（继承其换算快照与来源追溯） */
    private Long orderItemId;
    private Long skuId;
    /** 应收数量（基本单位） */
    private BigDecimal qtyExpected;
    /** 实收数量（基本单位，入库口径） */
    private BigDecimal qtyActual;
    /** 差异 = qty_expected − qty_actual */
    private BigDecimal qtyDiff;
    /** 0=无差异 1=短缺 2=破损 */
    private DiffType diffType;
    /** 0=接受 1=退货 2=补货 */
    private HandleType handleType;
    /** 已入库数量（基本单位，分次累加） */
    private BigDecimal qtyStored;
    /** 0=待处理 1=已完成 */
    private HandleStatus handleStatus;
    private String remark;
    /** P4 R3b 计重：实到重量（基本单位口径，valuation_type=1 时录入；<!-- D5 --> 净重/毛重/允许误差参数等拍板预留） */
    private BigDecimal actualWeight;
    /** P4 R3b 计重：合格量（硬校验：合格量 ≤ 实到重量，PRD L811） */
    private BigDecimal qualifiedQty;
}
