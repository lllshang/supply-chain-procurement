package com.dzgylxt.vo.purchase;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 询价单保存请求（设计 §2.2：仅 APPROVED 申请或明细子集）。 */
@Data
public class InquirySaveReqVO implements Serializable {

    /** 来源申请（APPROVED） */
    private Long applyId;
    /** 截标时间 */
    private LocalDateTime deadline;
    private String remark;
    /** 初始供应商范围（可空，发布前可增补） */
    private List<Long> supplierIds = new ArrayList<>();
}
