package com.dzgylxt.permission;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dzgylxt.entity.identity.SysMenu;
import com.dzgylxt.entity.identity.SysRole;
import com.dzgylxt.entity.identity.SysRoleMenu;
import com.dzgylxt.entity.identity.SysUserRole;
import com.dzgylxt.mapper.identity.SysMenuMapper;
import com.dzgylxt.mapper.identity.SysRoleMapper;
import com.dzgylxt.mapper.identity.SysRoleMenuMapper;
import com.dzgylxt.mapper.identity.SysUserRoleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RBAC 服务：用户→角色→菜单/权限 的解析。
 */
@Service
public class RbacService {

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysRoleMenuMapper sysRoleMenuMapper;

    @Autowired
    private SysMenuMapper sysMenuMapper;

    /** 用户拥有的角色 ID 集合 */
    public Set<Long> getUserRoleIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return sysUserRoleMapper
                .selectList(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).collect(Collectors.toSet());
    }

    /** 用户拥有的角色编码集合（如 SUPER_ADMIN） */
    public Set<String> getUserRoleCodes(Long userId) {
        Set<Long> roleIds = getUserRoleIds(userId);
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        return sysRoleMapper
                .selectList(Wrappers.<SysRole>lambdaQuery().in(SysRole::getId, roleIds))
                .stream().map(SysRole::getRoleCode).collect(Collectors.toSet());
    }

    /** 用户拥有的操作权限标识（菜单 perms 汇总） */
    public Set<String> getUserPerms(Long userId) {
        Set<Long> roleIds = getUserRoleIds(userId);
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> menuIds = sysRoleMenuMapper
                .selectList(Wrappers.<SysRoleMenu>lambdaQuery().in(SysRoleMenu::getRoleId, roleIds))
                .stream().map(SysRoleMenu::getMenuId).collect(Collectors.toSet());
        if (menuIds.isEmpty()) {
            return Set.of();
        }
        return sysMenuMapper
                .selectList(Wrappers.<SysMenu>lambdaQuery().in(SysMenu::getId, menuIds))
                .stream().map(SysMenu::getPerms)
                .filter(perms -> perms != null && !perms.isBlank())
                .flatMap(perms -> Arrays.stream(perms.split(",")))
                .map(String::trim)
                .collect(Collectors.toSet());
    }

    /** 用户可见菜单列表（按角色聚合，已排序、仅显示状态） */
    public List<SysMenu> getMenusByUser(Long userId) {
        Set<Long> roleIds = getUserRoleIds(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        Set<Long> menuIds = sysRoleMenuMapper
                .selectList(Wrappers.<SysRoleMenu>lambdaQuery().in(SysRoleMenu::getRoleId, roleIds))
                .stream().map(SysRoleMenu::getMenuId).collect(Collectors.toSet());
        if (menuIds.isEmpty()) {
            return List.of();
        }
        return sysMenuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                .in(SysMenu::getId, menuIds)
                .eq(SysMenu::getStatus, 0)
                .orderByAsc(SysMenu::getSort));
    }
}
