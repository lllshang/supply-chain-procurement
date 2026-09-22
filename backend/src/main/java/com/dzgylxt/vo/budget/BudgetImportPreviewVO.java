package com.dzgylxt.vo.budget;

import com.dzgylxt.vo.common.ImportErrorVO;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 年度预算导入预览（不落库）。
 */
@Data
public class BudgetImportPreviewVO implements Serializable {

    /** 模板版本 */
    private String templateVersion;
    private Integer year;
    private Long deptId;
    private BigDecimal totalAmount;
    /** 解析出的预算明细行（subject × period × project） */
    private List<BudgetLineRespVO> lines = new ArrayList<>();
    /** 错误清单（定位到行/列） */
    private List<ImportErrorVO> errors = new ArrayList<>();
    private Boolean valid;
}
