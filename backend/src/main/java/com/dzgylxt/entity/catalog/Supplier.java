package com.dzgylxt.entity.catalog;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.BlacklistFlag;
import com.dzgylxt.enums.CoopStatus;
import com.dzgylxt.enums.SupplierSource;
import com.dzgylxt.enums.SupplierStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 供应商。
 *
 * <p>{@code status}（{@link SupplierStatus}）为"资质状态"；{@link #coopStatus} 为"合作状态"，
 * 二者解耦，避免"资质审核中"误伤合作状态。{@code category}（String）为遗留自由文本，
 * 新增 {@link #supplierCategoryId}（Long）为强类型引用，后续以 {@code supplierCategoryId} 为准。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("supplier")
public class Supplier extends BaseEntity implements Serializable {

    private String name;
    /** 统一社会信用代码 */
    private String creditCode;
    private String level;
    /** 遗留自由文本分类（保留，不再写入） */
    private String category;
    /** 供应商分类ID（引用 supplier_category） */
    private Long supplierCategoryId;
    /** 资质状态：0=审核中 1=通过 2=驳回 */
    private SupplierStatus status;
    /** 合作状态：0=正常 1=停用 2=冻结 */
    private CoopStatus coopStatus;
    /** 黑名单：0=否 1=是 */
    private BlacklistFlag isBlacklist;
    /** 来源：0=平台录入 1=H5提交 2=导入 */
    private SupplierSource source;
    private String legalPerson;
    private String businessScope;
    private String contact;
    private String phone;
    private String bankName;
    private String bankAccount;
}
