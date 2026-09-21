package com.dzgylxt.controller;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.ApprovalTask;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 审批单据管理。 */
@RestController
@RequestMapping("/api/v1/approvals/tasks")
public class ApprovalTaskController extends BaseController<IService<ApprovalTask>, ApprovalTask> {
}
