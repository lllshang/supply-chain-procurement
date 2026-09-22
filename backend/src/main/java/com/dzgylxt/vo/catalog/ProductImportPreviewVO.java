package com.dzgylxt.vo.catalog;

import com.dzgylxt.vo.common.ImportErrorVO;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 产品导入预览（不落库）：展开后的 SPU/SKU 预览行 + 错误定位。
 */
@Data
public class ProductImportPreviewVO implements Serializable {

    /** 模板版本（校验通过时回显） */
    private String templateVersion;
    /** 展开后待落库的行（多规格已按组合展开） */
    private List<ProductImportRowVO> rows = new ArrayList<>();
    /** 错误清单（定位到行/列） */
    private List<ImportErrorVO> errors = new ArrayList<>();
    /** 是否全部校验通过 */
    private Boolean valid;
}
