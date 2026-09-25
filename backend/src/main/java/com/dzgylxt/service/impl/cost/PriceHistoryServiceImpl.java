package com.dzgylxt.service.impl.cost;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
    public List<PriceHistory> pendingList() {
        return list(Wrappers.<PriceHistory>lambdaQuery()
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
}
