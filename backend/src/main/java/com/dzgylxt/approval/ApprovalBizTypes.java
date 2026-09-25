package com.dzgylxt.approval;

/**
 * P4 审批业务类型字典（8 个 bizType，设计 §1.4 种子；发起点契约零变更——
 * 业务侧仍以字符串字面量传入 {@link ApprovalTaskSpec#setBizType}，
 * 本类仅供引擎/测试/配置管理单点引用，避免拼写漂移）。
 */
public final class ApprovalBizTypes {

    public static final String PURCHASE_APPLY = "PURCHASE_APPLY";
    public static final String AWARD = "AWARD";
    public static final String CONTRACT = "CONTRACT";
    public static final String FULFILLMENT_ADJUST = "FULFILLMENT_ADJUST";
    public static final String BUDGET = "BUDGET";
    public static final String SETTLEMENT = "SETTLEMENT";
    public static final String SUPPLIER_QUAL = "SUPPLIER_QUAL";
    public static final String DAILY_AUTH = "DAILY_AUTH";

    private ApprovalBizTypes() {
    }

    /** 全集（8 个，对齐 approval_flow_def 种子；契约测试锁定）。 */
    public static java.util.List<String> all() {
        return java.util.List.of(PURCHASE_APPLY, AWARD, CONTRACT, FULFILLMENT_ADJUST,
                BUDGET, SETTLEMENT, SUPPLIER_QUAL, DAILY_AUTH);
    }
}
