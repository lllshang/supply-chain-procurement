package com.dzgylxt.vo.order;

import com.dzgylxt.enums.AdjustBizType;
import com.dzgylxt.enums.AdjustType;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/** 履约调整保存请求（设计 §2.8：金额 ≤ 合同 5% 免审，超阈值走 FULFILLMENT_ADJUST 审批 <!-- D5 -->）。 */
@Data
public class AdjustSaveReqVO implements Serializable {

    /** 0=订单 1=到货 */
    private AdjustBizType bizType;
    private Long orderId;
    private Long arrivalId;
    /** 0=差异 1=退货 2=补货 3=变更 */
    private AdjustType adjustType;
    /** 调整涉及金额（绝对值，阈值判定依据） */
    private BigDecimal amount;
    private String beforeJson;
    private String afterJson;
    private String reason;
    private List<String> fileKeys;
}
