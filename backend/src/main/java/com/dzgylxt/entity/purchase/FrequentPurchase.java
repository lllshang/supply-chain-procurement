package com.dzgylxt.entity.purchase;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.FrequentStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 部门常购清单（设计 §1.2.3；申请快速带入，数据权限按 dept_id 隔离）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("frequent_purchase")
public class FrequentPurchase extends BaseEntity implements Serializable {

    /** 部门（数据权限隔离） */
    private Long deptId;
    /** SKU（仅有效 SKU 可维护） */
    private Long skuId;
    /** 默认数量（采购单位） */
    private BigDecimal defaultQty;
    /** 默认采购单位（unit.code） */
    private String purchaseUnit;
    /** 最近价（最近订单/报价价，无则 standard_price） */
    private BigDecimal lastPrice;
    private String remark;
    /** 0=正常 1=停用 */
    private FrequentStatus status;
}
