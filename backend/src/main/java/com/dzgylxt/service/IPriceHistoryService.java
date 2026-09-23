package com.dzgylxt.service;

import com.dzgylxt.entity.cost.PriceHistory;

import java.math.BigDecimal;
import java.util.List;

/**
 * 价格库服务（P3 设计 §1.4 / T07）。
 *
 * <p>成交价沉淀（三埋点）：报价采纳（QUOTATION）/ 定标审批通过（AWARD）/
 * 订单生成（ORDER）；异常价（偏离近期均价超过 {@code app.price.abnormal-percent}，
 * 默认 30%）落 {@code PENDING} 待审，其余直接 {@code APPROVED}。</p>
 *
 * <p>比价历史取数：{@link #recent} 仅取 {@code APPROVED} 价；<b>空库兜底</b>——
 * 调用方（比价视图）在返回为空时回退实时计算（近 5 次有效报价）。</p>
 */
public interface IPriceHistoryService {

    /**
     * 沉淀一条成交价（异常价自动入待审，不抛错——埋点失败不阻断业务主流程语义上
     * 由调用方同事务保证一致性；价格非法时静默忽略）。
     */
    void record(Long skuId, Long supplierId, BigDecimal price,
                com.dzgylxt.enums.PriceSource source, String bizType, Long bizId, String remark);

    /** 某 SKU 近 N 条通过价（时间倒序；仅 APPROVED；空库返回空列表，调用方兜底实时计算）。 */
    List<PriceHistory> recent(Long skuId, int limit);

    /** 待审价列表（价格审核页）。 */
    List<PriceHistory> pendingList();

    /** 审核待审价（通过/驳回；仅 PENDING 可流转）。 */
    void audit(Long id, boolean approved, String remark);
}
