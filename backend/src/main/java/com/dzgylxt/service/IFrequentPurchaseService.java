package com.dzgylxt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.purchase.FrequentPurchase;
import com.dzgylxt.vo.purchase.ApplyItemDraftVO;

import java.util.List;

/** 部门常购清单服务（设计 §2.1）。 */
public interface IFrequentPurchaseService extends IService<FrequentPurchase> {

    /** 部门隔离查询（DataPermissionInterceptor 兜底过滤）。 */
    List<FrequentPurchase> listByDept(Long deptId);

    /** 带入：停用 SKU 拒绝；最近价取最近订单/报价价，无则 standard_price；带入后由前端重算快照与金额。 */
    List<ApplyItemDraftVO> bringIn(Long deptId, List<Long> skuIds);
}
