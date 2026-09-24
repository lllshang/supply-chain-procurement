package com.dzgylxt.entity.settlement;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.common.BaseEntity;
import com.dzgylxt.enums.SettleMode;
import com.dzgylxt.enums.SettlementStatus;
import com.dzgylxt.enums.SettlementType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 结算单（P3 设计 §1.3.1；双入口：订单 draftFromOrder / 到货 draftFromArrival）。
 *
 * <p>状态机（订单维度视图）：PENDING →(SETTLEMENT 审批通过) SETTLED（触发预算核销 +
 * 订单全部结清收口）；驳回保持 PENDING 留痕可重提（规格口径，P4 如需显式 REJECTED 再扩）。
 * 部分结算为订单维度派生展示（已结金额 &lt; 应结总额）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("settlement")
public class Settlement extends BaseEntity implements Serializable {
    private Long orderId;
    private Long arrivalId;
    /** 结算单号 JS-{yyyy}{MM}-{seq6}（全局唯一） */
    private String settleNo;
    /** 本次结算数量（基本单位累计口径） */
    private BigDecimal settledQtyBase;
    /** 服务考核扣款合计（物料=0；取数 service_assess <!-- D8 已落地 P3 -->） */
    private BigDecimal deductAmount;
    /** 追溯合同（nullable，展示比对用） */
    private Long contractId;
    private SettlementType type;
    private BigDecimal amount;
    private SettlementStatus status;
    /** 结算方式：一次性/阶段/尾款 */
    private SettleMode settleMode;
    /** 阶段号（settle_mode=PHASE 时必填） */
    private Integer phaseNo;
    /** 阶段比例%（Σ=100 校验） */
    private BigDecimal phaseRatio;
    /** 是否尾款结清：0/1 */
    private Integer isFinal;
    /** R4：付款阶段（1=预付款 2=进度款/阶段结算 3=尾款；一次性/物料结算可空，P3-2 口径） */
    private Integer paymentStage;
    /** R4：本单抵扣的预付款合计（尾款结算自动扣减，committed 口径含在途；非尾款结算恒 0） */
    private BigDecimal prepaymentDeduction;
    private String remark;

    // ---------------- PB-01 结算维度派生付款进度（VO 派生，不落库） ----------------

    /** PB-01：Σ已确认付款（该结算单，不落库） */
    @TableField(exist = false)
    private BigDecimal paidAmount;
    /** PB-01：结算应付 = 结算金额 − 已抵扣预付（不落库） */
    @TableField(exist = false)
    private BigDecimal payableAmount;
    /** PB-01：派生付款状态——UNPAID(未付款)/PARTIAL(部分付款)/PAID(已付清)，按 paid vs payable 计算（不落库） */
    @TableField(exist = false)
    private String payStatus;
    /** PB-01：派生付款进度 = paid/payable（0~1 封顶，不落库） */
    @TableField(exist = false)
    private BigDecimal paidProgress;
}
