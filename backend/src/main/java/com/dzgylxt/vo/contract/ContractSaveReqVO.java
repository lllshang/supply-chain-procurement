package com.dzgylxt.vo.contract;

import com.dzgylxt.enums.ItemType;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 合同保存请求（设计 §2.5）。 */
@Data
public class ContractSaveReqVO implements Serializable {

    private Long supplierId;
    /** 来源定标（P2b 线下补录可空 <!-- D2 -->） */
    private Long awardId;
    private String title;
    /** 合同类型（#33 枚举 name 契约：收 name/数值、吐 name；落库 getValue()） */
    private ItemType contractType;
    /** 合同金额（有来源定标时必须 = Σ 定标明细） */
    private BigDecimal amount;
    /** 预算科目（S8：统计冗余，P2b 选填） */
    private Long subjectId;
    private LocalDate validFrom;
    private LocalDate validTo;
    /** 附件 JSON 数组（file_meta.file_key，多附件） */
    private List<String> fileKeys;
    private String remark;
}
