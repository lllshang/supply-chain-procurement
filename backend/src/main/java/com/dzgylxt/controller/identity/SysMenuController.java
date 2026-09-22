package com.dzgylxt.controller.identity;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.identity.SysMenu;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dzgylxt.controller.BaseController;

/** 菜单管理（CRUD）。动态菜单见 {@link MenuController}。 */
@RestController
@RequestMapping("/api/v1/rbac/menus")
public class SysMenuController extends BaseController<IService<SysMenu>, SysMenu> {
}
