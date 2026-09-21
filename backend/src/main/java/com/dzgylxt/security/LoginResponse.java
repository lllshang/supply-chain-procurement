package com.dzgylxt.security;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 登录响应。
 */
@Data
public class LoginResponse implements Serializable {

    private String token;
    private String tokenType = "Bearer";
    private long expiresIn;
    private UserInfo user;

    @Data
    public static class UserInfo implements Serializable {
        private Long id;
        private String username;
        private String nickname;
        private Long deptId;
        private List<String> roles;
        private List<String> perms;
    }
}
