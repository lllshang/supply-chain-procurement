package com.dzgylxt.service.impl.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.catalog.Sku;
import com.dzgylxt.entity.catalog.SpecOption;
import com.dzgylxt.enums.CatalogStatus;
import com.dzgylxt.mapper.catalog.SkuMapper;
import com.dzgylxt.mapper.catalog.SpecOptionMapper;
import com.dzgylxt.service.ISpecOptionService;
import com.dzgylxt.vo.catalog.SpecOptionSaveReqVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 规格配置服务实现。 */
@Service
public class SpecOptionServiceImpl extends ServiceImpl<SpecOptionMapper, SpecOption>
        implements ISpecOptionService {

    private final SkuMapper skuMapper;

    public SpecOptionServiceImpl(SkuMapper skuMapper) {
        this.skuMapper = skuMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSpecOption(SpecOptionSaveReqVO req) {
        if (!StringUtils.hasText(req.getSpecName()) || !StringUtils.hasText(req.getSpecValue())) {
            throw new BizException(ResultCode.PARAM_ERROR, "规格名与规格值必填");
        }
        if (baseMapper.existsNameValue(req.getSpecName(), req.getSpecValue(), null)) {
            throw new BizException(ResultCode.DATA_CONFLICT, "规格值已存在：" + req.getSpecValue());
        }
        SpecOption entity = new SpecOption();
        entity.setSpecName(req.getSpecName());
        entity.setSpecValue(req.getSpecValue());
        entity.setStatus(req.getStatus() == null ? CatalogStatus.VALID : req.getStatus());
        save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSpecOption(Long id, SpecOptionSaveReqVO req) {
        SpecOption entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "规格值不存在：" + id);
        }
        String name = StringUtils.hasText(req.getSpecName()) ? req.getSpecName() : entity.getSpecName();
        String value = StringUtils.hasText(req.getSpecValue()) ? req.getSpecValue() : entity.getSpecValue();
        if (baseMapper.existsNameValue(name, value, id)) {
            throw new BizException(ResultCode.DATA_CONFLICT, "规格值已存在：" + value);
        }
        entity.setSpecName(name);
        entity.setSpecValue(value);
        if (req.getStatus() != null) {
            entity.setStatus(req.getStatus());
        }
        updateById(entity);
    }

    @Override
    public Map<String, List<String>> groupedOptions() {
        List<SpecOption> all = list(new LambdaQueryWrapper<SpecOption>()
                .eq(SpecOption::getStatus, CatalogStatus.VALID)
                .orderByAsc(SpecOption::getSpecName)
                .orderByAsc(SpecOption::getId));
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (SpecOption option : all) {
            grouped.computeIfAbsent(option.getSpecName(), k -> new ArrayList<>()).add(option.getSpecValue());
        }
        return grouped;
    }

    @Override
    public void invalidate(Long id) {
        SpecOption entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "规格值不存在：" + id);
        }
        if (isReferenced(entity.getSpecValue())) {
            throw new BizException(ResultCode.BIZ_ERROR, "规格值被 SKU 引用，不可置无效");
        }
        entity.setStatus(CatalogStatus.INVALID);
        updateById(entity);
    }

    private boolean isReferenced(String specValue) {
        if (!StringUtils.hasText(specValue)) {
            return false;
        }
        // SKU.spec 为自由文本（"规格名=规格值"），按包含关系做软校验
        return skuMapper.selectCount(new LambdaQueryWrapper<Sku>().like(Sku::getSpec, specValue)) > 0;
    }
}
