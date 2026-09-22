package com.dzgylxt.service.impl.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.catalog.Sku;
import com.dzgylxt.entity.catalog.SupplierSku;
import com.dzgylxt.entity.catalog.Unit;
import com.dzgylxt.entity.catalog.UnitConversion;
import com.dzgylxt.mapper.catalog.SkuMapper;
import com.dzgylxt.mapper.catalog.SupplierSkuMapper;
import com.dzgylxt.mapper.catalog.UnitConversionMapper;
import com.dzgylxt.mapper.catalog.UnitMapper;
import com.dzgylxt.service.IUnitService;
import com.dzgylxt.vo.catalog.UnitSaveReqVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 计量单位字典服务实现。 */
@Service
public class UnitServiceImpl extends ServiceImpl<UnitMapper, Unit> implements IUnitService {

    private static final int STATUS_VALID = 0;
    private static final int STATUS_INVALID = 1;

    private final SkuMapper skuMapper;
    private final SupplierSkuMapper supplierSkuMapper;
    private final UnitConversionMapper unitConversionMapper;

    public UnitServiceImpl(SkuMapper skuMapper,
                           SupplierSkuMapper supplierSkuMapper,
                           UnitConversionMapper unitConversionMapper) {
        this.skuMapper = skuMapper;
        this.supplierSkuMapper = supplierSkuMapper;
        this.unitConversionMapper = unitConversionMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createUnit(UnitSaveReqVO req) {
        if (!StringUtils.hasText(req.getCode()) || !StringUtils.hasText(req.getName())) {
            throw new BizException(ResultCode.PARAM_ERROR, "单位编码与名称必填");
        }
        if (baseMapper.existsByCode(req.getCode(), null)) {
            throw new BizException(ResultCode.DATA_CONFLICT, "单位编码已存在：" + req.getCode());
        }
        Unit entity = new Unit();
        entity.setCode(req.getCode());
        entity.setName(req.getName());
        entity.setStatus(req.getStatus() == null ? STATUS_VALID : req.getStatus());
        save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUnit(Long id, UnitSaveReqVO req) {
        Unit entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "单位不存在：" + id);
        }
        if (StringUtils.hasText(req.getCode())
                && !req.getCode().equals(entity.getCode())
                && baseMapper.existsByCode(req.getCode(), id)) {
            throw new BizException(ResultCode.DATA_CONFLICT, "单位编码已存在：" + req.getCode());
        }
        if (StringUtils.hasText(req.getCode())) {
            entity.setCode(req.getCode());
        }
        if (StringUtils.hasText(req.getName())) {
            entity.setName(req.getName());
        }
        if (req.getStatus() != null) {
            entity.setStatus(req.getStatus());
        }
        updateById(entity);
    }

    @Override
    public void invalidate(Long id) {
        Unit entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "单位不存在：" + id);
        }
        if (isReferenced(entity.getCode())) {
            throw new BizException(ResultCode.BIZ_ERROR, "单位被 SKU/换算/绑定引用，不可置无效");
        }
        entity.setStatus(STATUS_INVALID);
        updateById(entity);
    }

    @Override
    public void assertExists(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BizException(ResultCode.PARAM_ERROR, "单位编码不可为空");
        }
        Unit unit = getOne(new LambdaQueryWrapper<Unit>().eq(Unit::getCode, code).last("LIMIT 1"));
        if (unit == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "单位不存在：" + code);
        }
    }

    private boolean isReferenced(String code) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        boolean skuRef = skuMapper.selectCount(new LambdaQueryWrapper<Sku>()
                .and(w -> w.eq(Sku::getBaseUnit, code).or().eq(Sku::getPurchaseUnit, code))) > 0;
        boolean bindRef = supplierSkuMapper.selectCount(new LambdaQueryWrapper<SupplierSku>()
                .eq(SupplierSku::getPackageUnit, code)) > 0;
        boolean convRef = unitConversionMapper.selectCount(new LambdaQueryWrapper<UnitConversion>()
                .and(w -> w.eq(UnitConversion::getFromUnit, code).or().eq(UnitConversion::getToUnit, code))) > 0;
        return skuRef || bindRef || convRef;
    }
}
