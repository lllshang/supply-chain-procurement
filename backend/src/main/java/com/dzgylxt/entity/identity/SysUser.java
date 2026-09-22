package com.dzgylxt.entity.identity;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.UserStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    /**
     * 明文密码（仅创建用户的请求入参，P3-20）。
     *
     * <p>{@code exist = false} 保证 MyBatis-Plus 不映射该列（绝不落库）；
     * {@code WRITE_ONLY} 保证仅参与请求反序列化、不回显到任何响应；
     * {@code transient} 防止实体被序列化传输时携带明文。
     * 实际入库值由 {@code SysUserServiceImpl#save} 编码后的 BCrypt 哈希写入
     * {@code passwordHash}。</p>
     */
    @TableField(exist = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private transient String password;

    private String nickname;
    private Long mainDeptId;
    private String email;
    private String phone;
    private UserStatus status;
    private LocalDateTime lastLoginAt;
}
