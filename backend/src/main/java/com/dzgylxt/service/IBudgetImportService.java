package com.dzgylxt.service;

import com.dzgylxt.vo.budget.BudgetImportPreviewVO;
import com.dzgylxt.vo.common.ImportTaskVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 年度预算导入服务（R-BUD-03）。
 */
public interface IBudgetImportService {

    /** 年度预算导入：模板版本校验→解析→生成 header + line(period 0 与 1–12)。 */
    ImportTaskVO importAnnual(MultipartFile file);

    /** 预览（不落库），错误定位到行/列。 */
    BudgetImportPreviewVO preview(MultipartFile file);

    /** 异步导入进度查询（R1）。 */
    ImportTaskVO taskStatus(String taskId);
}
