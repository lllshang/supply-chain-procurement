package com.dzgylxt.vo.catalog;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 价格规则新增/编辑请求。
 */
@Data
public class PriceRuleSaveReqVO implements Serializable {

    /** 规则类型：1=最低限价 2=最高限价 3=区间 4=公式 */
    private Integer ruleType;
    /** 引用类型：1=商品(SPU) 2=品类 */
    private Integer refType;
    /** 引用对象ID */
    private Long refId;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    /** 公式表达式（ruleType=4 时必填） */
    private String expression;
    /** 0=有效 1=无效 */
    private Integer status;
}
