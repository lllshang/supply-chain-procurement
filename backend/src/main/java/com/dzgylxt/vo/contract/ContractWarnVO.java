package com.dzgylxt.vo.contract;

import com.dzgylxt.entity.contract.Contract;
import lombok.Data;

import java.io.Serializable;

/** 合同到期预警（设计 §2.5：valid_to − 提前天数配置，默认 30 天）。 */
@Data
public class ContractWarnVO implements Serializable {

    private Contract contract;
    /** 距到期天数（可为负 = 已过期但状态未刷新） */
    private Long daysToExpire;
    private String message;
}
