package com.dzgylxt.controller.settlement;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.settlement.Settlement;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dzgylxt.controller.BaseController;

/** 结算管理。 */
@RestController
@RequestMapping("/api/v1/settlements")
public class SettlementController extends BaseController<IService<Settlement>, Settlement> {
}
