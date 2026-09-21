package com.dzgylxt.security;

import com.dzgylxt.common.BizException;

/**
 * 认证提供方适配接口（一期 Local，后续可扩展 SsoAuthProvider）。
 */
public interface AuthProvider {

    /**
     * 校验用户名密码，返回登录主体。失败时抛出 {@link BizException}。
     */
    LoginUser authenticate(String username, String password);
}
