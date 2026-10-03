package com.dzgylxt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.catalog.PriceRule;
import com.dzgylxt.vo.catalog.PriceRuleSaveReqVO;

import java.math.BigDecimal;

/**
 * 价格规则服务。
 */
public interface IPriceRuleService extends IService<PriceRule> {

    /** 新增规则：依 rule_type 校验 min/max/expression 组合。 */
    Long createRule(PriceRuleSaveReqVO req);

    /** 编辑规则。 */
    void updateRule(Long id, PriceRuleSaveReqVO req);

    /** 建档/改价时校验价格是否越界（R-PRD-09），越界抛 BizException。 */
    void validatePrice(Integer refType, Long refId, BigDecimal price);

    /** 置无效：被引用则拒绝（价格规则本身可置无效）。 */
    void invalidate(Long id);

    /** 采购最低价同步标准价：读取开关状态与上次更新数量。 */
    com.dzgylxt.vo.catalog.LowestOrderSyncVO getLowestOrderSync();

    /** 采购最低价同步标准价：持久化开关；开启时按最低采购单价回写 SPU 标准价，返回更新条数。 */
    int applyLowestOrderSync(boolean enabled);
}
