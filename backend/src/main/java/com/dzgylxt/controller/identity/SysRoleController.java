package com.dzgylxt.controller.identity;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.identity.SysRole;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dzgylxt.controller.BaseController;

/** 角色管理。 */
@RestController
@RequestMapping("/api/v1/rbac/roles")
public class SysRoleController extends BaseController<IService<SysRole>, SysRole> {
}
