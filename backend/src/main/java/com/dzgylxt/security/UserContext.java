package com.dzgylxt.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

/**
 * 当前登录用户上下文（从 SecurityContext 读取）。
 */
public final class UserContext {

    private UserContext() {
    }

    public static LoginUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser) {
            return (LoginUser) auth.getPrincipal();
        }
        return null;
    }

    public static Long getCurrentUserId() {
        LoginUser u = get();
        return u == null ? null : u.getId();
    }

    public static String getCurrentUsername() {
        LoginUser u = get();
        return u == null ? null : u.getUsername();
    }

    public static Long getCurrentDeptId() {
        LoginUser u = get();
        return u == null ? null : u.getMainDeptId();
    }

    public static List<String> getRoles() {
        LoginUser u = get();
        return u == null ? Collections.emptyList() : u.getRoles();
    }

    public static List<String> getPerms() {
        LoginUser u = get();
        return u == null ? Collections.emptyList() : u.getPerms();
    }

    public static boolean hasPerm(String perm) {
        return getPerms().contains(perm);
    }
}
