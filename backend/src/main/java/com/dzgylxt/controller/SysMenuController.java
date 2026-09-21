package com.dzgylxt.controller;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.SysMenu;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 菜单管理（CRUD）。动态菜单见 {@link MenuController}。 */
@RestController
@RequestMapping("/api/v1/rbac/menus")
public class SysMenuController extends BaseController<IService<SysMenu>, SysMenu> {
}
