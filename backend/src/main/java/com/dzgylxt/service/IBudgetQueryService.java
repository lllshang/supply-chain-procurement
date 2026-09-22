package com.dzgylxt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dzgylxt.vo.budget.BudgetHeaderQueryReqVO;
import com.dzgylxt.vo.budget.BudgetHeaderRespVO;
import com.dzgylxt.vo.budget.BudgetLineRespVO;

import java.util.List;

/**
 * 预算查询服务（部门数据权限过滤由 DataPermissionInterceptor 注入）。
 */
public interface IBudgetQueryService {

    /** 预算头分页（部门数据权限过滤）。 */
    IPage<BudgetHeaderRespVO> pageHeader(BudgetHeaderQueryReqVO req);

    /** 台账明细（period 维度：0=年，1–12=月）。 */
    List<BudgetLineRespVO> listLines(Long headerId);
}
