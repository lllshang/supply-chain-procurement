package com.dzgylxt.controller.contract;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.contract.Contract;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dzgylxt.controller.BaseController;

/** 合同管理。 */
@RestController
@RequestMapping("/api/v1/contracts")
public class ContractController extends BaseController<IService<Contract>, Contract> {
}
