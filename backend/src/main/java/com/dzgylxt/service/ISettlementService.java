package com.dzgylxt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.settlement.Settlement;
import com.dzgylxt.vo.settlement.SettlementDraftVO;
import com.dzgylxt.vo.settlement.SettlementSaveReqVO;

/**
 * 结算服务（P3 设计 §2.1 / T04）。
 *
 * <p>状态机（订单维度视图）：PENDING →(SETTLEMENT 审批通过) SETTLED（触发预算核销 +
 * 订单全部结清→SETTLED）；驳回保持 PENDING 留痕可重提（规格口径）。</p>
 */
public interface ISettlementService extends IService<Settlement> {

    /** 订单入口带出：明细价、已入库量、可结余量、服务扣款、合同累计结算。 */
    SettlementDraftVO draftFromOrder(Long orderId);

    /** 到货单入口（优先）：按 arrival_item.qty_stored 汇总带出。 */
    SettlementDraftVO draftFromArrival(Long arrivalId);

    /** 创建结算单（校验：订单状态/数量≤入库合格累计/重复结算/阶段比例 Σ≤100/尾款=应结总额）。 */
    Long createSettlement(SettlementSaveReqVO req);

    /** 修改重提（驳回留痕后）：仅 PENDING 可改。 */
    void updateSettlement(Long id, SettlementSaveReqVO req);

    /** 发起 SETTLEMENT 审批。 */
    Long submit(Long id);

    /** SETTLEMENT 审批回调（由 SettlementApprovalHandler 委托）：通过→核销+SETTLED；驳回→保持 PENDING。 */
    void handleApproval(Long taskId, Long bizId, boolean approved, String comment);

    /** 分页（订单/供应商/状态过滤）。 */
    com.baomidou.mybatisplus.core.metadata.IPage<Settlement> page(long current, long size,
                                                                  Long orderId, Long supplierId,
                                                                  com.dzgylxt.enums.SettlementStatus status);
}
