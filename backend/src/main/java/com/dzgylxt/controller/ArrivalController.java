package com.dzgylxt.controller;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.Arrival;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 到货验收管理。 */
@RestController
@RequestMapping("/api/v1/arrivals")
public class ArrivalController extends BaseController<IService<Arrival>, Arrival> {
}
