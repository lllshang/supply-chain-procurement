package com.dzgylxt.service.impl.cost;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.cost.PriceHistory;
import com.dzgylxt.enums.PriceAuditStatus;
import com.dzgylxt.enums.PriceSource;
import com.dzgylxt.mapper.cost.PriceHistoryMapper;
import com.dzgylxt.security.UserContext;
import com.dzgylxt.service.IPriceHistoryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 价格库实现（P3 设计 §1.4 / T07）。
 *
 * <p>异常价判定：与该 SKU 近期通过价的均价偏离超过
 * {@code app.price.abnormal-percent}（默认 20%，对齐 P3 设计 §1.4）→ {@code PENDING} 待审；
 * 无历史价时首条直接 {@code APPROVED}（首价无基准，规格口径）。</p>
 */
@Service
public class PriceHistoryServiceImpl extends ServiceImpl<PriceHistoryMapper, PriceHistory>
        implements IPriceHistoryService {

    /** 异常价偏离阈值（百分比，默认 20 = 对齐 P3 设计 §1.4；可由 application.yml 覆盖）。 */
    @Value("${app.price.abnormal-percent:20}")
    private Integer abnormalPercent;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void record(Long skuId, Long supplierId, BigDecimal price,
                       PriceSource source, String bizType, Long bizId, String remark) {
        // 价格非法静默忽略（埋点不阻断业务主流程；校验责任在源头服务）
        if (skuId == null || price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        PriceHistory h = new PriceHistory();
        h.setSkuId(skuId);
        h.setSupplierId(supplierId);
        h.setPrice(price);
        h.setSource(source);
        h.setBizType(bizType);
        h.setBizId(bizId);
        h.setEffectiveDate(LocalDate.now());
        h.setRemark(remark);
        h.setAuditStatus(auditStatusOf(skuId, price));
        save(h);
    }

    /** 异常价判定：偏离近期通过价均价超阈值 → PENDING；无历史 → APPROVED。 */
    private PriceAuditStatus auditStatusOf(Long skuId, BigDecimal price) {
        List<PriceHistory> history = recent(skuId, 10);
        if (history.isEmpty()) {
            return PriceAuditStatus.APPROVED;
        }
        BigDecimal sum = history.stream()
                .map(PriceHistory::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(history.size()), 6, RoundingMode.HALF_UP);
        BigDecimal deviation = price.subtract(avg).abs()
                .multiply(BigDecimal.valueOf(100))
                .divide(avg, 2, RoundingMode.HALF_UP);
        return deviation.compareTo(BigDecimal.valueOf(abnormalPercent)) > 0
                ? PriceAuditStatus.PENDING : PriceAuditStatus.APPROVED;
    }

    @Override
    public List<PriceHistory> recent(Long skuId, int limit) {
        return list(Wrappers.<PriceHistory>lambdaQuery()
                .eq(PriceHistory::getSkuId, skuId)
                .eq(PriceHistory::getAuditStatus, PriceAuditStatus.APPROVED)
                .orderByDesc(PriceHistory::getId)
                .last("LIMIT " + Math.max(1, limit)));
    }

    @Override
    public IPage<PriceHistory> pendingList(IPage<PriceHistory> page) {
        return page(page, Wrappers.<PriceHistory>lambdaQuery()
                .eq(PriceHistory::getAuditStatus, PriceAuditStatus.PENDING)
                .orderByDesc(PriceHistory::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, boolean approved, String remark) {
        PriceHistory h = getById(id);
        if (h == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "价格记录不存在：" + id);
        }
        if (h.getAuditStatus() != PriceAuditStatus.PENDING) {
            throw new BizException(ResultCode.STATUS_INVALID, "仅待审价格可审核");
        }
        h.setAuditStatus(approved ? PriceAuditStatus.APPROVED : PriceAuditStatus.REJECTED);
        h.setAuditBy(UserContext.getCurrentUserId());
        h.setAuditAt(java.time.LocalDateTime.now());
        if (remark != null && !remark.isBlank()) {
            h.setRemark(remark);
        }
        updateById(h);
    }

    // ==================== P4 R3c：最低价同步标准价 ====================

    /** 开关（默认关，Q12）：仅影响后续订单完成，不回刷历史。 */
    @org.springframework.beans.factory.annotation.Value("${app.price.lowest-price-sync-enabled:false}")
    private boolean lowestPriceSyncEnabled;

    @org.springframework.beans.factory.annotation.Autowired
    private com.dzgylxt.mapper.order.OrderItemMapper orderItemMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private com.dzgylxt.mapper.order.PurchaseOrderMapper purchaseOrderMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private com.dzgylxt.mapper.catalog.SkuMapper skuMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncLowestPriceOnOrderReceived(Long orderId) {
        if (!lowestPriceSyncEnabled || orderId == null) {
            return 0; // 关闭时零行为（AC④）
        }
        List<com.dzgylxt.entity.order.OrderItem> items = orderItemMapper.selectList(
                Wrappers.<com.dzgylxt.entity.order.OrderItem>lambdaQuery()
                        .eq(com.dzgylxt.entity.order.OrderItem::getOrderId, orderId));
        com.dzgylxt.entity.order.PurchaseOrder order = purchaseOrderMapper.selectById(orderId);
        int updated = 0;
        java.util.Set<Long> seenSkus = new java.util.HashSet<>();
        for (com.dzgylxt.entity.order.OrderItem item : items) {
            if (item.getSkuId() == null || item.getPrice() == null
                    || item.getPrice().compareTo(BigDecimal.ZERO) <= 0
                    || !seenSkus.add(item.getSkuId())) {
                continue;
            }
            String calibre = calibreKey(item.getConvSnapshot());
            BigDecimal lowest = lowestPositivePrice(item.getSkuId(), calibre);
            if (lowest == null) {
                continue;
            }
            com.dzgylxt.entity.catalog.Sku sku = skuMapper.selectById(item.getSkuId());
            if (sku == null || (sku.getStandardPrice() != null
                    && sku.getStandardPrice().compareTo(lowest) == 0)) {
                continue;
            }
            sku.setStandardPrice(lowest.setScale(2, RoundingMode.HALF_UP));
            skuMapper.updateById(sku);
            // 同步动作留痕（price_history 通道；MANUAL 源 + PRICE_SYNC 业务类型）
            record(item.getSkuId(), order == null ? null : order.getSupplierId(), lowest,
                    PriceSource.MANUAL, "PRICE_SYNC", orderId,
                    "最低价同步标准价（口径 " + calibre + "）");
            updated++;
        }
        return updated;
    }

    /** 同 SKU 同口径已完成订单的最低正数成交价（口径=conv_snapshot 采购单位+换算率，PRD L521 禁止跨口径）。 */
    private BigDecimal lowestPositivePrice(Long skuId, String calibre) {
        List<com.dzgylxt.entity.order.OrderItem> candidates = orderItemMapper.selectList(
                Wrappers.<com.dzgylxt.entity.order.OrderItem>lambdaQuery()
                        .eq(com.dzgylxt.entity.order.OrderItem::getSkuId, skuId)
                        .gt(com.dzgylxt.entity.order.OrderItem::getPrice, BigDecimal.ZERO));
        BigDecimal min = null;
        for (com.dzgylxt.entity.order.OrderItem candidate : candidates) {
            // 防御性过滤非正价格（SQL 侧 gt(0) 已过滤，Java 侧再兜底一层）
            if (candidate.getPrice() == null || candidate.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (!calibre.equals(calibreKey(candidate.getConvSnapshot()))) {
                continue;
            }
            com.dzgylxt.entity.order.PurchaseOrder po =
                    purchaseOrderMapper.selectById(candidate.getOrderId());
            // 有效订单=已完成（RECEIVED）及之后（不含取消）；历史 SETTLED/PAID 兼容计入
            if (po == null || po.getStatus() == null
                    || po.getStatus() == com.dzgylxt.enums.OrderStatus.CREATED
                    || po.getStatus() == com.dzgylxt.enums.OrderStatus.CANCELLED) {
                continue;
            }
            if (min == null || candidate.getPrice().compareTo(min) < 0) {
                min = candidate.getPrice();
            }
        }
        return min;
    }

    /** 口径键：conv_snapshot（purchaseUnit + rate）；空快照回退 "default"。 */
    private String calibreKey(String convSnapshot) {
        if (convSnapshot == null || convSnapshot.isBlank()) {
            return "default";
        }
        try {
            cn.hutool.json.JSONObject json = cn.hutool.json.JSONUtil.parseObj(convSnapshot);
            return json.getStr("purchaseUnit", "default") + "@" + json.getStr("rate", "1");
        } catch (Exception e) {
            return "default";
        }
    }
}
