package com.dzgylxt.vo.purchase;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 报价导入结果（设计 §2.3：错误明细随 errors 列表返回；错误 Sheet xlsx 改由独立端点
 *  /import/error-sheet/{batchNo} 按批次流式下载，B4）。 */
@Data
public class QuotationImportResultVO implements Serializable {

    /** 本批次号 BJ-{inquiry_no}-{seq2} */
    private String batchNo;
    private int total;
    private int success;
    private int fail;
    private List<String> errors = new ArrayList<>();
}
