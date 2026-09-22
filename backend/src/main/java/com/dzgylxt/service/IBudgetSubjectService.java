package com.dzgylxt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.budget.BudgetSubject;
import com.dzgylxt.vo.budget.BudgetSubjectSaveReqVO;
import com.dzgylxt.vo.common.CategoryTreeNodeVO;

import java.util.List;

/**
 * 预算科目服务。
 */
public interface IBudgetSubjectService extends IService<BudgetSubject> {

    Long createSubject(BudgetSubjectSaveReqVO req);

    void updateSubject(Long id, BudgetSubjectSaveReqVO req);

    /** 科目树。 */
    List<CategoryTreeNodeVO> tree();

    /** 置无效：被预算明细引用则拒绝。 */
    void invalidate(Long id);
}
