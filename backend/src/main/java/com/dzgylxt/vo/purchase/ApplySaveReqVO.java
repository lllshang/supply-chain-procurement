package com.dzgylxt.vo.purchase;

import com.dzgylxt.enums.PurchaseApplyType;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 采购申请保存请求（设计 §2.1）。 */
@Data
public class ApplySaveReqVO implements Serializable {

    private String title;
    /** 申请类型：0=标准（P2 仅标准链路），1=日常/框架、2=线下补录为 P2b 预留 <!-- D1/D2 --> */
    private PurchaseApplyType type;
    /** 期望到货日期 */
    private LocalDate expectedDate;
    private String remark;
    private List<ApplyItemReqVO> items = new ArrayList<>();
}
