package com.dzgylxt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.settlement.Settlement;
import com.dzgylxt.vo.settlement.SettlementDraftVO;
import com.dzgylxt.vo.settlement.SettlementSaveReqVO;

import java.math.BigDecimal;
import java.util.Collection;

/**
 * 结算服务（P3 设计 §2.1 / T04；R4 预付款结算 + R5 状态机收敛）。
 *
 * <p>状态机（订单维度视图）：PENDING →(SETTLEMENT 审批通过) SETTLED（触发预算核销）；
 * 驳回保持 PENDING 留痕可重提（规格口径）。R5：订单状态机不再流转 SETTLED/PAID，
 * 结清/付清进度改派生展示字段（见订单 VO）；R4：预付款从订单发起（无需到货）→ 审批 →
 * 登记实付 → 尾款结算自动扣减（prepaymentDeduction）。</p>
 */
public interface ISettlementService extends IService<Settlement> {

    /** 订单入口带出：明细价、已入库量、可结余量、服务扣款、合同累计结算、已付预付款。 */
    SettlementDraftVO draftFromOrder(Long orderId);

    /** 到货单入口（优先）：按 arrival_item.qty_stored 汇总带出。 */
    SettlementDraftVO draftFromArrival(Long arrivalId);

    /** R4：预付款结算草稿（订单发起，带出已付预付款）。 */
    SettlementDraftVO draftPrepaymentFromOrder(Long orderId);

    /** 创建结算单（校验：订单状态/数量≤入库合格累计/重复结算/阶段比例 Σ≤100/尾款=应结总额−预付款抵扣）。 */
    Long createSettlement(SettlementSaveReqVO req);

    /** R4：预付款结算（订单发起，无需到货/无结算数量）→ SETTLEMENT 审批 → 预算核销；累计预付款 ≤ 订单有效金额。 */
    Long createPrepaymentSettlement(Long orderId, BigDecimal amount, String remark);

    /** 修改重提（驳回留痕后）：仅 PENDING 可改。 */
    void updateSettlement(Long id, SettlementSaveReqVO req);

    /** 发起 SETTLEMENT 审批。 */
    Long submit(Long id);

    /** SETTLEMENT 审批回调（由 SettlementApprovalHandler 委托）：通过→核销+SETTLED；驳回→保持 PENDING。 */
    void handleApproval(Long taskId, Long bizId, boolean approved, String comment);

    /** B9：作废结算单——仅 PENDING 可作废（SETTLED 已核销不可逆）；释放 committed 口径。 */
    void voidSettlement(Long id, String reason);

    /** 分页（订单/供应商/状态过滤）。 */
    com.baomidou.mybatisplus.core.metadata.IPage<Settlement> page(long current, long size,
                                                                  Long orderId, Long supplierId,
                                                                  com.dzgylxt.enums.SettlementStatus status);

    /**
     * PB-01（口径 B）：回填结算维度派生付款进度——paid=Σ已确认付款、
     * payable=结算应付（P2-R2-1：直接取 amount，尾款单创建时已净额化，不再减抵扣）、
     * payStatus=UNPAID/PARTIAL/PAID、paidProgress=paid/payable（0~1 封顶）。
     * VO 派生不落库，语义载体=结算单（付款记录无中间态）。
     */
    void fillPayProgress(Collection<Settlement> settlements);
}
