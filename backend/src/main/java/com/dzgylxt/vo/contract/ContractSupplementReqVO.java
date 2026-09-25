package com.dzgylxt.vo.contract;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合同补充签订请求（P4 R3a，PRD L849/L1063：补充协议关联原合同，走 CONTRACT 审批）。
 */
@Data
public class ContractSupplementReqVO implements Serializable {

    /** 补充协议标题（缺省自动生成：原合同标题-补充协议） */
    private String title;
    /** 补充金额（正数） */
    private BigDecimal amount;
    private LocalDate validFrom;
    private LocalDate validTo;
    /** 合同类型字典引用（缺省继承原合同 type_id） */
    private Long typeId;
    /** 补充原因/内容摘要 */
    private String remark;
}
