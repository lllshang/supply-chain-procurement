package com.dzgylxt.controller.identity;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.identity.SysDept;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dzgylxt.controller.BaseController;

/** 部门管理。 */
@RestController
@RequestMapping("/api/v1/org/depts")
public class SysDeptController extends BaseController<IService<SysDept>, SysDept> {
}
