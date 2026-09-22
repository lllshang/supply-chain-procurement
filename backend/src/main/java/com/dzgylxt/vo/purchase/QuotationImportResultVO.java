package com.dzgylxt.vo.purchase;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 报价导入结果（设计 §2.3：错误 Sheet 口径——错误行列表返回）。 */
@Data
public class QuotationImportResultVO implements Serializable {

    /** 本批次号 BJ-{inquiry_no}-{seq2} */
    private String batchNo;
    private int total;
    private int success;
    private int fail;
    private List<String> errors = new ArrayList<>();
    /**
     * 错误 Sheet（xlsx 字节的 base64，单表「行号/原因」列）。
     *
     * <p>仅校验失败（整批不落库，AC④ 全有或全无）时填充，前端解码后可直接下载。</p>
     */
    private String errorSheetBase64;
}
