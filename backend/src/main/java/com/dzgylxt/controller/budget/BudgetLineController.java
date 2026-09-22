package com.dzgylxt.controller.budget;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.budget.BudgetLine;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dzgylxt.controller.BaseController;

/** 预算明细管理。 */
@RestController
@RequestMapping("/api/v1/budgets/lines")
public class BudgetLineController extends BaseController<IService<BudgetLine>, BudgetLine> {
}
