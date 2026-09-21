package com.dzgylxt.entity;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.SupplierStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 供应商。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("supplier")
public class Supplier extends BaseEntity implements Serializable {

    private String name;
    /** 统一社会信用代码 */
    private String creditCode;
    private String level;
    private String category;
    private SupplierStatus status;
    private String legalPerson;
    private String businessScope;
    private String contact;
    private String phone;
    private String bankName;
    private String bankAccount;
}
