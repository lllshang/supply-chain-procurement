package com.dzgylxt.vo.supplier;

import com.dzgylxt.enums.SupplierSource;
import lombok.Data;

import java.io.Serializable;

/**
 * 供应商新增/编辑请求。
 */
@Data
public class SupplierSaveReqVO implements Serializable {

    private String name;
    private String creditCode;
    private String level;
    /** 供应商三级分类ID */
    private Long supplierCategoryId;
    private String legalPerson;
    private String businessScope;
    private String contact;
    private String phone;
    private String bankName;
    private String bankAccount;
    /** 来源：0=平台录入 1=H5提交 2=导入（缺省 0） */
    private SupplierSource source;
}
