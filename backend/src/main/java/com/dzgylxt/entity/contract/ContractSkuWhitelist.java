package com.dzgylxt.entity.contract;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 合同 SKU 白名单（D15 方案 A 兜底，规格 §6）。
 *
 * <p>合同无价格清单且无关联定标时的兜底供货范围；白名单为空 = 维持现网行为（额度闸兜底）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("contract_sku_whitelist")
public class ContractSkuWhitelist extends BaseEntity implements Serializable {

    /** 合同 id（无清单且无定标合同的兜底供货范围） */
    private Long contractId;
    /** 允许下单的 SKU */
    private Long skuId;
    /** 维护原因（审计） */
    private String remark;
}
