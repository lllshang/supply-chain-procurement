package com.dzgylxt.controller.identity;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.identity.SysUser;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dzgylxt.controller.BaseController;

/** 用户管理。 */
@RestController
@RequestMapping("/api/v1/org/users")
public class SysUserController extends BaseController<IService<SysUser>, SysUser> {
}
