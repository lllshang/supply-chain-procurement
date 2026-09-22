package com.dzgylxt.controller.approval;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.approval.ApprovalTask;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dzgylxt.controller.BaseController;

/** 审批单据管理。 */
@RestController
@RequestMapping("/api/v1/approvals/tasks")
public class ApprovalTaskController extends BaseController<IService<ApprovalTask>, ApprovalTask> {
}
