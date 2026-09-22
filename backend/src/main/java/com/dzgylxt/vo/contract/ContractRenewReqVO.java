package com.dzgylxt.vo.contract;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 合同续签请求（设计 §2.5：新合同 renewed_from_id=原 id，独立走审批）。 */
@Data
public class ContractRenewReqVO implements Serializable {

    private BigDecimal amount;
    private LocalDate validFrom;
    private LocalDate validTo;
    private List<String> fileKeys;
    private String remark;
}
