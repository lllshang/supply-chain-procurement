package com.dzgylxt.controller;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.Settlement;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 结算管理。 */
@RestController
@RequestMapping("/api/v1/settlements")
public class SettlementController extends BaseController<IService<Settlement>, Settlement> {
}
