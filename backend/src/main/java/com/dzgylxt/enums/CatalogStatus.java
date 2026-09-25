package com.dzgylxt.enums;

import com.baomidou.mybatisplus.annotation.IEnum;

/**
 * 通用「有效 / 无效」状态（数值枚举，API 层序列化为数值，QA #33 数值组）。
 *
 * <p>归属字段（DB 整型，MyBatis-Plus 按 {@code getValue()} 映射）：
 * budget_subject.status、budget_project.status、product_category.status、
 * supplier_category.status、spec_option.status、unit.status、price_rule.status，
 * 以及品类/分类/科目树节点 VO（{@code CategoryTreeNodeVO.status}）与若干 SaveReqVO /
 * {@code ProductExportReqVO}。0=有效，1=无效。</p>
 */
public enum CatalogStatus implements IEnum<Integer> {
    VALID(0, "有效"),
    INVALID(1, "无效");

    private final int code;
    private final String desc;

    CatalogStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    @com.fasterxml.jackson.annotation.JsonValue
    public Integer getValue() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
