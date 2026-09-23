package com.dzgylxt.vo.settlement;

import com.dzgylxt.enums.SettleMode;
import com.dzgylxt.enums.SettlementType;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 结算单创建/重提请求（P3 设计 §2.1）。
 *
 * <p>双入口：{@code orderId} 必填（订单维度）；{@code arrivalId} 到货单入口可选
 * （重复结算拦截以到货单为粒度）。物料结算金额 = 明细单价 × 本次结算数量（基本单位），
 * 服务结算金额 = 订单金额 − Σ考核扣款（<!-- D8 已落地 P3 -->），允许 req.amount 覆盖。</p>
 */
@Data
public class SettlementSaveReqVO implements Serializable {
    private Long orderId;
    /** 到货单入口（可空；重复结算拦截键） */
    private Long arrivalId;
    /** 本次结算数量（基本单位累计口径） */
    private BigDecimal settledQtyBase;
    /** 结算金额（可空=按数量×订单均价推导） */
    private BigDecimal amount;
    private SettlementType type;
    /** 结算方式：一次性/阶段/尾款 */
    private SettleMode settleMode;
    /** 阶段号（settleMode=PHASE 必填） */
    private Integer phaseNo;
    /** 阶段比例%（Σ 全部阶段 ≤ 100） */
    private BigDecimal phaseRatio;
    /** 尾款结清：1=本次后订单应结总额结清 */
    private Integer isFinal;
    private Long contractId;
    private String remark;
}
