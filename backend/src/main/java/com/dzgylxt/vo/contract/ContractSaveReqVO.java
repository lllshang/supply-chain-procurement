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
    /** 合同类型字典引用（P4 R3a；与存量 contractType TINYINT 并存，typeId 优先） */
    private Long typeId;
    /** 合同金额（有来源定标时必须 = Σ 定标明细） */
    private BigDecimal amount;
    /** 预算科目（S8：统计冗余，P2b 选填） */
    private Long subjectId;
    private LocalDate validFrom;
    private LocalDate validTo;
    /** 附件 JSON 数组（file_meta.file_key，多附件） */
    private List<String> fileKeys;
    private String remark;

    /**
     * P3c-A1：手工价格清单明细（无来源定标时维护；有定标时忽略，
     * 由 {@code award_item} 自动继承生成 source_type=1）。
     */
    private List<PriceItemVO> priceItems;

    /** P3c-A1：手工价格清单行。 */
    @Data
    public static class PriceItemVO implements Serializable {
        private Long skuId;
        /** 含税单价（基本单位口径） */
        private BigDecimal unitPrice;
        /** 数量（基本单位口径） */
        private BigDecimal qty;
        private String remark;
    }
}
