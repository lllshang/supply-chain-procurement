package com.dzgylxt.controller;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.SysUser;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户管理。 */
@RestController
@RequestMapping("/api/v1/org/users")
public class SysUserController extends BaseController<IService<SysUser>, SysUser> {
}
