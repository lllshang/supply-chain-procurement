package com.dzgylxt.controller;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.SysRole;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 角色管理。 */
@RestController
@RequestMapping("/api/v1/rbac/roles")
public class SysRoleController extends BaseController<IService<SysRole>, SysRole> {
}
