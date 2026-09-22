package com.dzgylxt.entity.purchase;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.ItemType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 采购申请明细（申请↔订单追溯）。
 *
 * <p>P2 §1.3.2 换算快照重构：{@code unit_snapshot}(String) <b>弃用停写</b>（仅历史展示），
 * 改用结构化三字段 {@code qty_in_purchase_unit / qty_in_base_unit / conv_rate_snapshot}；
 * {@code version} 为下单余量扣减的乐观锁兜底。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("purchase_apply_item")
public class PurchaseApplyItem extends BaseEntity implements Serializable {

    private Long applyId;
    private Long skuId;
    /** 采购单位（unit.code，P2 新增） */
    private String purchaseUnit;
    /** 采购单位数量（P2 新增） */
    private BigDecimal qtyInPurchaseUnit;
    /** 基本单位数量 = qty × 换算率（P2 新增） */
    private BigDecimal qtyInBaseUnit;
    /** 换算快照（unit_conversion 当前生效版本，P2 新增） */
    private BigDecimal convRateSnapshot;
    /** 预估单价（P2 新增） */
    private BigDecimal priceEstimate;
    /** 行级类型：0=物料 1=服务（转单拆分依据，P2 新增） */
    private ItemType itemType;
    private String remark;
    /** 乐观锁版本（下单余量扣减并发兜底，P2 新增） */
    private Integer version;

    /** @deprecated P2 起弃用停写（仅历史展示），改用结构化换算快照三字段。 */
    @Deprecated
    private String unitSnapshot;

    private BigDecimal qty;
    /** 申请数量（余量基准：ordered_qty + 本单数量 ≤ apply_qty，见设计 §4.1 规则3） */
    private BigDecimal applyQty;
    private BigDecimal orderedQty;
    private BigDecimal remainQty;
}
