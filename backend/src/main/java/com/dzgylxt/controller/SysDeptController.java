package com.dzgylxt.controller;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.SysDept;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 部门管理。 */
@RestController
@RequestMapping("/api/v1/org/depts")
public class SysDeptController extends BaseController<IService<SysDept>, SysDept> {
}
