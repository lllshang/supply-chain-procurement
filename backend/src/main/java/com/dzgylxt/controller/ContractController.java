package com.dzgylxt.controller;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.Contract;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 合同管理。 */
@RestController
@RequestMapping("/api/v1/contracts")
public class ContractController extends BaseController<IService<Contract>, Contract> {
}
