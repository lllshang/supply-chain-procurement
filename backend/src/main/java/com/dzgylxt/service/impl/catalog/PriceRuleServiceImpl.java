package com.dzgylxt.service.impl.catalog;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.catalog.PriceRule;
import com.dzgylxt.entity.catalog.Spu;
import com.dzgylxt.enums.PriceRefType;
import com.dzgylxt.enums.PriceRuleType;
import com.dzgylxt.mapper.catalog.PriceRuleMapper;
import com.dzgylxt.mapper.catalog.SpuMapper;
import com.dzgylxt.service.IPriceRuleService;
import com.dzgylxt.vo.catalog.PriceRuleSaveReqVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/** 价格规则服务实现。 */
@Service
public class PriceRuleServiceImpl extends ServiceImpl<PriceRuleMapper, PriceRule> implements IPriceRuleService {

    private static final int STATUS_VALID = 0;
    private static final int STATUS_INVALID = 1;

    private final SpuMapper spuMapper;

    public PriceRuleServiceImpl(SpuMapper spuMapper) {
        this.spuMapper = spuMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRule(PriceRuleSaveReqVO req) {
        PriceRule entity = new PriceRule();
        applyAndValidate(entity, req);
        entity.setStatus(req.getStatus() == null ? STATUS_VALID : req.getStatus());
        save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRule(Long id, PriceRuleSaveReqVO req) {
        PriceRule entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "价格规则不存在：" + id);
        }
        applyAndValidate(entity, req);
        if (req.getStatus() != null) {
            entity.setStatus(req.getStatus());
        }
        updateById(entity);
    }

    @Override
    public void validatePrice(Integer refType, Long refId, BigDecimal price) {
        if (price == null) {
            return;
        }
        Long spuId = null;
        Long categoryId = null;
        if (PriceRefType.SPU.getValue().equals(refType)) {
            spuId = refId;
            Spu spu = refId == null ? null : spuMapper.selectById(refId);
            categoryId = spu == null ? null : spu.getCategoryId();
        } else if (PriceRefType.CATEGORY.getValue().equals(refType)) {
            categoryId = refId;
        } else {
            return;
        }
        List<PriceRule> rules = baseMapper.selectEffectiveByRef(spuId, categoryId);
        for (PriceRule rule : rules) {
            checkRule(rule, price);
        }
    }

    @Override
    public void invalidate(Long id) {
        PriceRule entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "价格规则不存在：" + id);
        }
        entity.setStatus(STATUS_INVALID);
        updateById(entity);
    }

    /** 依据 rule_type 校验字段组合并写回实体。 */
    private void applyAndValidate(PriceRule entity, PriceRuleSaveReqVO req) {
        if (req.getRuleType() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "规则类型必填");
        }
        PriceRuleType type = null;
        for (PriceRuleType t : PriceRuleType.values()) {
            if (t.getValue().equals(req.getRuleType())) {
                type = t;
                break;
            }
        }
        if (type == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "未知规则类型：" + req.getRuleType());
        }
        if (req.getRefType() == null || req.getRefId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "引用类型与引用对象必填");
        }
        switch (type) {
            case MIN_LIMIT -> {
                if (req.getMinPrice() == null) {
                    throw new BizException(ResultCode.PARAM_ERROR, "最低限价必填 minPrice");
                }
            }
            case MAX_LIMIT -> {
                if (req.getMaxPrice() == null) {
                    throw new BizException(ResultCode.PARAM_ERROR, "最高限价必填 maxPrice");
                }
            }
            case RANGE -> {
                if (req.getMinPrice() == null || req.getMaxPrice() == null) {
                    throw new BizException(ResultCode.PARAM_ERROR, "区间规则必填 minPrice/maxPrice");
                }
                if (req.getMinPrice().compareTo(req.getMaxPrice()) > 0) {
                    throw new BizException(ResultCode.PARAM_ERROR, "区间下界不可大于上界");
                }
            }
            case FORMULA -> {
                if (!StringUtils.hasText(req.getExpression())) {
                    throw new BizException(ResultCode.PARAM_ERROR, "公式规则必填 expression");
                }
            }
            default -> throw new BizException(ResultCode.PARAM_ERROR, "未知规则类型");
        }
        entity.setRuleType(type);
        entity.setRefType(PriceRefType.SPU.getValue().equals(req.getRefType())
                ? PriceRefType.SPU : PriceRefType.CATEGORY);
        entity.setRefId(req.getRefId());
        entity.setMinPrice(req.getMinPrice());
        entity.setMaxPrice(req.getMaxPrice());
        entity.setExpression(req.getExpression());
    }

    /** 单条规则越界校验。 */
    private void checkRule(PriceRule rule, BigDecimal price) {
        if (rule.getRuleType() == null) {
            return;
        }
        switch (rule.getRuleType()) {
            case MIN_LIMIT -> {
                if (rule.getMinPrice() != null && price.compareTo(rule.getMinPrice()) < 0) {
                    throw new BizException(ResultCode.BIZ_ERROR, "价格低于最低限价 " + rule.getMinPrice());
                }
            }
            case MAX_LIMIT -> {
                if (rule.getMaxPrice() != null && price.compareTo(rule.getMaxPrice()) > 0) {
                    throw new BizException(ResultCode.BIZ_ERROR, "价格高于最高限价 " + rule.getMaxPrice());
                }
            }
            case RANGE -> {
                if (rule.getMinPrice() != null && price.compareTo(rule.getMinPrice()) < 0) {
                    throw new BizException(ResultCode.BIZ_ERROR, "价格低于区间下界 " + rule.getMinPrice());
                }
                if (rule.getMaxPrice() != null && price.compareTo(rule.getMaxPrice()) > 0) {
                    throw new BizException(ResultCode.BIZ_ERROR, "价格高于区间上界 " + rule.getMaxPrice());
                }
            }
            default -> {
                // FORMULA 暂不在建档阶段求值，留待审批复核
            }
        }
    }
}
