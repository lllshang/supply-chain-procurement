package com.dzgylxt.entity.identity;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.UserStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统用户。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_user")
public class SysUser extends BaseEntity implements Serializable {

    private String username;
    private String passwordHash;
    private String nickname;
    private Long mainDeptId;
    private String email;
    private String phone;
    private UserStatus status;
    private LocalDateTime lastLoginAt;
}
