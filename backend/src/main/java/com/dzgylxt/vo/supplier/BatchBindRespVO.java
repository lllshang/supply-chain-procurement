package com.dzgylxt.vo.supplier;

import com.dzgylxt.vo.common.ImportErrorVO;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量绑定结果。
 */
@Data
public class BatchBindRespVO implements Serializable {

    private Integer totalRows;
    private Integer successRows;
    private Integer failRows;
    private List<ImportErrorVO> errors = new ArrayList<>();
}
