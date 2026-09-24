package com.dzgylxt.entity.contract;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 合同价格清单（P3c-A1 / PRD BR-26 L1101：合同价格必须与订单一致）。
 *
 * <p>双来源（规格裁决②）：{@code sourceType=1} 由 {@code award_item} 单价×数量自动继承
 * （有定标结果的合同）；{@code sourceType=2} 手工维护（框架/定价协议等无定标来源）。
 * 下单第四重校验以本表为准：{@code order_item} 每行必须命中 sku 且价格一致，
 * 未命中或价不符 → 4000 硬拦截（无旁路）。</p>
 *
 * <p><b>免检约定（主理人裁决，P3c 实施）</b>：合同类型字典属 P4 延后项，本批不引入
 * {@code FRAME_EMPTY} 类型标记；改以「该合同是否存在价格清单记录」作为免检判据——
 * 无清单（框架协议仅控金额、存量合同）→ 跳过第四重，走合同额度闸兜底。
 * 既有清单的合同一律强制校验，不削弱"硬拦截"裁决。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("contract_price_item")
public class ContractPriceItem extends BaseEntity implements Serializable {

    /** 合同ID（逻辑外键 contract.id） */
    private Long contractId;
    /** SKU（逻辑外键 sku.id） */
    private Long skuId;
    /** 含税单价（基本单位口径，与 order_item.price 同口径） */
    private BigDecimal unitPrice;
    /** 数量（基本单位口径） */
    private BigDecimal qty;
    /** 来源：1=定标继承 2=手工维护 */
    private Integer sourceType;
    private String remark;
}
