package com.dzgylxt.entity;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.DataScope;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 用户-部门多归属（含数据权限范围）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_user_dept")
public class SysUserDept extends BaseEntity implements Serializable {

    private Long userId;
    private Long deptId;
    private DataScope dataScope;
}
