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

    /**
     * P4 R3c：订单完成（入库落账点）触发最低价同步标准价。
     *
     * <p>开关 {@code app.price.lowest-price-sync-enabled}（默认关，Q12）：开启时对订单内
     * 每个 SKU，取同 <b>SKU + 采购单位 + 换算口径</b>（conv_snapshot 口径键，禁止跨口径比较
     * PRD L521）的已完成（RECEIVED）订单中最低正数成交价 → 写 {@code sku.standard_price}，
     * 同步动作经 {@link #record} 走 price_history 留痕。关闭时零行为；切换只影响后续订单不回刷历史。</p>
     *
     * @return 实际更新标准价的 SKU 数（0=开关关/无更新）
     */
    int syncLowestPriceOnOrderReceived(Long orderId);
}
