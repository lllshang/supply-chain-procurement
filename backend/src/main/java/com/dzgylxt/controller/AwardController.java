package com.dzgylxt.controller;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.Award;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 定标管理。 */
@RestController
@RequestMapping("/api/v1/awards")
public class AwardController extends BaseController<IService<Award>, Award> {
}
