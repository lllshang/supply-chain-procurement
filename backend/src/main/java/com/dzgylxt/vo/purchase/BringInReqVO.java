package com.dzgylxt.vo.purchase;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 常购清单带入请求（设计 §2.1）。 */
@Data
public class BringInReqVO implements Serializable {

    /** 部门（数据权限内） */
    private Long deptId;
    private List<Long> skuIds;
}
