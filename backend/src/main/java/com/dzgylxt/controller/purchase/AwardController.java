package com.dzgylxt.controller.purchase;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.purchase.Award;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dzgylxt.controller.BaseController;

/** 定标管理。 */
@RestController
@RequestMapping("/api/v1/awards")
public class AwardController extends BaseController<IService<Award>, Award> {
}
