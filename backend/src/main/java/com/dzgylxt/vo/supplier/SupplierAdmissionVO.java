package com.dzgylxt.vo.supplier;

import com.dzgylxt.enums.CoopStatus;
import com.dzgylxt.enums.BlacklistFlag;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 供应商准入资格（R-X-01 派生只读）。
 *
 * <p>{@code qualified = coopStatus==0 && isBlacklist==0 && 存在未过期资质（VALID/EXPIRING）}；
 * 本阶段只计算与暴露，不做拦截（拦截点由 P2/P3 落地）。</p>
 */
@Data
public class SupplierAdmissionVO implements Serializable {

    private Long supplierId;
    private Boolean qualified;
    /** 合作状态（#33 name 契约：快照吐 name） */
    private CoopStatus coopStatus;
    /** 黑名单（#33 name 契约） */
    private BlacklistFlag isBlacklist;
    /** 资质有效期整体派生状态：VALID / EXPIRING / EXPIRED / NONE */
    private String qualValidity;
    /** 不合格原因列表 */
    private List<String> reasons = new ArrayList<>();
}
