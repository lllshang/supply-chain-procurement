package com.dzgylxt.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 方法级鉴权辅助 Bean（在 {@code @PreAuthorize} 的 SpEL 中以 {@code @authz} 引用）。
 *
 * <p>阶段一鉴权策略：后台管理端点要求「已登录且至少拥有一个权限」。
 * 0 角色 / 0 权限用户即使持有合法 token，也无法读取或写入任何管理数据（纵深防御）。
 * 后续阶段应按菜单 {@code perms} 细化到每个端点的 {@code @PreAuthorize("hasAuthority('contract:edit')")}。</p>
 */
@Component("authz")
public class AuthzService {

    /**
     * 当前登录用户是否至少拥有一个权限。
     */
    public boolean hasAnyPerm(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof LoginUser user)) {
            return false;
        }
        return user.getPerms() != null && !user.getPerms().isEmpty();
    }
}
