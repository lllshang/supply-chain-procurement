package com.dzgylxt.entity.identity;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.DataScope;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 部门（树形，承接数据权限）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_dept")
public class SysDept extends BaseEntity implements Serializable {

    private Long parentId;
    private String deptName;
    private String treePath;
    private DataScope dataScope;
    private Integer sort;
    private String leader;
    private String phone;
    /** 0=正常，1=停用 */
    private Integer status;
}
