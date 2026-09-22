package com.dzgylxt.controller.budget;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.budget.BudgetHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dzgylxt.controller.BaseController;

/** 预算管理（年度预算头）。 */
@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetHeaderController extends BaseController<IService<BudgetHeader>, BudgetHeader> {
}
