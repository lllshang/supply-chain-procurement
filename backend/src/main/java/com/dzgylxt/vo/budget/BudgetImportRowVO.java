package com.dzgylxt.vo.budget;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 年度预算导入行模型（一行 = 一个 科目×项目，含 12 个月列）。
 *
 * <p>列口径：年度总额 + 1–12 月。若 {@code monthN} 均空则以 {@code annual} 落 period=0；
 * 否则按 {@code monthN} 落 period=1..12，period=0 取 {@code annual}（空则=各月之和）。</p>
 */
@Data
public class BudgetImportRowVO implements Serializable {

    @ExcelProperty("年份")
    private Integer year;
    @ExcelProperty("部门ID")
    private Long deptId;
    @ExcelProperty("科目编码")
    private String subjectCode;
    @ExcelProperty("项目编码")
    private String projectCode;
    @ExcelProperty("年度总额")
    private BigDecimal annual;
    @ExcelProperty("1月")
    private BigDecimal month1;
    @ExcelProperty("2月")
    private BigDecimal month2;
    @ExcelProperty("3月")
    private BigDecimal month3;
    @ExcelProperty("4月")
    private BigDecimal month4;
    @ExcelProperty("5月")
    private BigDecimal month5;
    @ExcelProperty("6月")
    private BigDecimal month6;
    @ExcelProperty("7月")
    private BigDecimal month7;
    @ExcelProperty("8月")
    private BigDecimal month8;
    @ExcelProperty("9月")
    private BigDecimal month9;
    @ExcelProperty("10月")
    private BigDecimal month10;
    @ExcelProperty("11月")
    private BigDecimal month11;
    @ExcelProperty("12月")
    private BigDecimal month12;

    /** 按月份下标（1–12）取月额度，越界返回 null。 */
    public BigDecimal monthAt(int index) {
        switch (index) {
            case 1: return month1;
            case 2: return month2;
            case 3: return month3;
            case 4: return month4;
            case 5: return month5;
            case 6: return month6;
            case 7: return month7;
            case 8: return month8;
            case 9: return month9;
            case 10: return month10;
            case 11: return month11;
            case 12: return month12;
            default: return null;
        }
    }
}
