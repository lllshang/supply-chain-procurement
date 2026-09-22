package com.dzgylxt.vo.budget;

import com.dzgylxt.vo.common.ImportErrorVO;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 导入结果响应。
 */
@Data
public class ImportResultRespVO implements Serializable {

    private Boolean success;
    private Integer totalRows;
    private Integer errorRows;
    private List<ImportErrorVO> errors = new ArrayList<>();
    /** 生成的预算头ID（导入成功时） */
    private Long headerId;
    private String errorFileKey;
}
