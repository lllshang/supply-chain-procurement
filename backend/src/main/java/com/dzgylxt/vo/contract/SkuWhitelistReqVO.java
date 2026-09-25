package com.dzgylxt.vo.contract;

import lombok.Data;

import java.io.Serializable;

/**
 * 合同 SKU 白名单维护行（P4 D15 方案 A 兜底，设计 §6.4）。
 */
@Data
public class SkuWhitelistReqVO implements Serializable {

    /** 允许下单的 SKU */
    private Long skuId;
    /** 维护原因（审计） */
    private String remark;
}
