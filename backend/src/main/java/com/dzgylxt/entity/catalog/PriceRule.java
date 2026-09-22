package com.dzgylxt.entity.catalog;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.common.BaseEntity;
import com.dzgylxt.enums.PriceRefType;
import com.dzgylxt.enums.PriceRuleType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 价格规则（最低限价/最高限价/区间/公式）。
 *
 * <p>可被 SPU 或品类引用（{@code refType + refId}），建档/改价时校验参考价/标准价是否越界。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("price_rule")
public class PriceRule extends BaseEntity implements Serializable {

    /** 规则类型：1=最低限价 2=最高限价 3=区间 4=公式 */
    private PriceRuleType ruleType;
    /** 引用类型：1=商品(SPU) 2=品类 */
    private PriceRefType refType;
    /** 引用对象ID（spu.id 或 product_category.id） */
    private Long refId;
    /** 最低价/区间下界 */
    private BigDecimal minPrice;
    /** 最高价/区间上界 */
    private BigDecimal maxPrice;
    /** 公式表达式（ruleType=4 时使用） */
    private String expression;
    /** 0=有效，1=无效 */
    private Integer status;
}
