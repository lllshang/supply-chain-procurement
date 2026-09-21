package com.dzgylxt.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.SysUser;
import com.dzgylxt.enums.UserStatus;
import com.dzgylxt.mapper.SysUserMapper;
import com.dzgylxt.permission.RbacService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * 本地账号认证实现（一期）。后续可新增 {@code SsoAuthProvider} 切换。
 */
@Service
@Primary
public class LocalAuthProvider implements AuthProvider {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private RbacService rbacService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public LoginUser authenticate(String username, String password) {
        SysUser user = sysUserMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new BizException(ResultCode.LOGIN_FAILED);
        }
        if (UserStatus.DISABLED.equals(user.getStatus())) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已禁用");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BizException(ResultCode.LOGIN_FAILED);
        }
        LoginUser loginUser = new LoginUser();
        loginUser.setId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setPassword(user.getPasswordHash());
        loginUser.setMainDeptId(user.getMainDeptId());
        loginUser.setRoles(new ArrayList<>(rbacService.getUserRoleCodes(user.getId())));
        loginUser.setPerms(new ArrayList<>(rbacService.getUserPerms(user.getId())));
        loginUser.setEnabled(UserStatus.ENABLED.equals(user.getStatus()) || user.getStatus() == null);
        return loginUser;
    }
}
