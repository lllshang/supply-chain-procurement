package com.dzgylxt.vo.settlement;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 供应商对账单（P3 设计 §2.2 / T06）：应付=Σ结算、已付=Σ付款、差额 + 明细清单。
 */
@Data
public class StatementVO implements Serializable {
    private Long supplierId;
    private LocalDate from;
    private LocalDate to;
    /** 应付总额（Σ 已结算结算单金额） */
    private BigDecimal totalPayable;
    /** 已付总额（Σ 已确认付款 PAID） */
    private BigDecimal totalPaid;
    /** 差额 = 应付 − 已付 */
    private BigDecimal balance;
    private List<Row> rows = new ArrayList<>();

    /** 对账单明细行（结算与付款各一行，direction 区分）。 */
    @Data
    public static class Row implements Serializable {
        /** 日期（结算=created_at，付款=pay_date） */
        private LocalDate date;
        /** JS-… / FK-… */
        private String docNo;
        /** SETTLEMENT / PAYMENT */
        private String direction;
        private BigDecimal amount;
        private String remark;
    }
}
