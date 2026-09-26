package com.dzgylxt.common;

/**
 * 鉴权相关常量（阶段一统一 GUARD 表达式）。
 */
public final class SecurityConstants {
    private SecurityConstants() {
    }

    /** 阶段一鉴权：已登录且至少拥有一个权限（纵深防御）。 */
    public static final String GUARD = "isAuthenticated() and @authz.hasAnyPerm(authentication)";
}
