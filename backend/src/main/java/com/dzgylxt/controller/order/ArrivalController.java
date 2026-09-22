package com.dzgylxt.controller.order;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.order.Arrival;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dzgylxt.controller.BaseController;

/** 到货验收管理。 */
@RestController
@RequestMapping("/api/v1/arrivals")
public class ArrivalController extends BaseController<IService<Arrival>, Arrival> {
}
