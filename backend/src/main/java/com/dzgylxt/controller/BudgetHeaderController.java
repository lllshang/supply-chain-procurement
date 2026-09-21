package com.dzgylxt.controller;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.BudgetHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 预算管理（年度预算头）。 */
@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetHeaderController extends BaseController<IService<BudgetHeader>, BudgetHeader> {
}
