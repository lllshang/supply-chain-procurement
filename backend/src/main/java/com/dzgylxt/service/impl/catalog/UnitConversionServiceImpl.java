package com.dzgylxt.service.impl.catalog;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.catalog.UnitConversion;
import com.dzgylxt.mapper.catalog.UnitConversionMapper;
import com.dzgylxt.service.ISkuService;
import com.dzgylxt.service.IUnitConversionService;
import com.dzgylxt.service.IUnitService;
import com.dzgylxt.vo.catalog.UnitConversionSaveReqVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 单位换算服务实现。 */
@Service
public class UnitConversionServiceImpl extends ServiceImpl<UnitConversionMapper, UnitConversion>
        implements IUnitConversionService {

    private final ISkuService skuService;
    private final IUnitService unitService;

    public UnitConversionServiceImpl(ISkuService skuService, IUnitService unitService) {
        this.skuService = skuService;
        this.unitService = unitService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveConversion(UnitConversionSaveReqVO req) {
        if (req.getSkuId() == null || skuService.getById(req.getSkuId()) == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "SKU 不存在：" + req.getSkuId());
        }
        if (!StringUtils.hasText(req.getFromUnit()) || !StringUtils.hasText(req.getToUnit())) {
            throw new BizException(ResultCode.PARAM_ERROR, "原单位与目标单位必填");
        }
        if (req.getFromUnit().equals(req.getToUnit())) {
            throw new BizException(ResultCode.PARAM_ERROR, "原单位与目标单位不可相同");
        }
        if (req.getRate() == null || req.getRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "换算率必须大于 0");
        }
        unitService.assertExists(req.getFromUnit());
        unitService.assertExists(req.getToUnit());

        Integer maxVersion = baseMapper.maxVersion(req.getSkuId(), req.getFromUnit(), req.getToUnit());
        UnitConversion entity = new UnitConversion();
        entity.setSkuId(req.getSkuId());
        entity.setFromUnit(req.getFromUnit());
        entity.setToUnit(req.getToUnit());
        entity.setRate(req.getRate());
        entity.setEffectiveFrom(req.getEffectiveFrom() == null ? LocalDateTime.now() : req.getEffectiveFrom());
        entity.setVersion((maxVersion == null ? 0 : maxVersion) + 1);
        save(entity);
        return entity.getId();
    }

    @Override
    public UnitConversion currentEffective(Long skuId, String fromUnit) {
        // 基准时间用应用时钟，与 saveConversion 写入 effective_from 的时钟保持一致，
        // 避免"DB 时区 ≠ 应用时区"时 NOW() 比较恒不命中（见 Mapper Javadoc）。
        return baseMapper.selectCurrentEffective(skuId, fromUnit, LocalDateTime.now());
    }

    @Override
    public List<UnitConversion> history(Long skuId) {
        return baseMapper.selectHistory(skuId);
    }
}
