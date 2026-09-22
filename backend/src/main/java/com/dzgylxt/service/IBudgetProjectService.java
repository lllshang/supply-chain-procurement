package com.dzgylxt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.budget.BudgetProject;
import com.dzgylxt.vo.budget.BudgetProjectSaveReqVO;

import java.util.List;

/**
 * 预算项目服务。
 */
public interface IBudgetProjectService extends IService<BudgetProject> {

    Long createProject(BudgetProjectSaveReqVO req);

    void updateProject(Long id, BudgetProjectSaveReqVO req);

    void invalidate(Long id);

    List<BudgetProject> listByYear(Integer year);
}
