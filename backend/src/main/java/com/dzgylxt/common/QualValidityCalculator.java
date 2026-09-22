package com.dzgylxt.common;

import com.dzgylxt.enums.QualValidity;

import java.time.LocalDateTime;

/**
 * 资质有效期派生计算（R-SUP-04）。
 */
public final class QualValidityCalculator {

    private QualValidityCalculator() {
    }

    /**
     * 依据到期时间与预警提前天数派生有效期状态。
     *
     * @param expireAt  到期时间（null=长期有效）
     * @param warnDays  预警提前天数
     */
    public static QualValidity compute(LocalDateTime expireAt, int warnDays) {
        if (expireAt == null) {
            return QualValidity.VALID;
        }
        LocalDateTime now = LocalDateTime.now();
        if (expireAt.isBefore(now)) {
            return QualValidity.EXPIRED;
        }
        if (!expireAt.isAfter(now.plusDays(Math.max(warnDays, 0)))) {
            return QualValidity.EXPIRING;
        }
        return QualValidity.VALID;
    }
}
