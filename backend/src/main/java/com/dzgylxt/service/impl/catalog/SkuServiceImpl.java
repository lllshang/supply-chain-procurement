package com.dzgylxt.service.impl.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.catalog.Sku;
import com.dzgylxt.enums.ProductStatus;
import com.dzgylxt.enums.PriceRefType;
import com.dzgylxt.enums.ValuationType;
import com.dzgylxt.mapper.catalog.SkuMapper;
import com.dzgylxt.service.IPriceRuleService;
import com.dzgylxt.service.ISkuService;
import com.dzgylxt.service.ISpuService;
import com.dzgylxt.service.IUnitService;
import com.dzgylxt.vo.catalog.SkuPageReqVO;
import com.dzgylxt.vo.catalog.SkuPageRespVO;
import com.dzgylxt.vo.catalog.SkuSaveReqVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 商品 SKU 服务实现。 */
@Service
public class SkuServiceImpl extends ServiceImpl<SkuMapper, Sku> implements ISkuService {

    private static final ProductStatus STATUS_NORMAL = ProductStatus.NORMAL;
    private static final ProductStatus STATUS_DISABLED = ProductStatus.DISABLED;

    private final ISpuService spuService;
    private final IUnitService unitService;
    private final IPriceRuleService priceRuleService;

    public SkuServiceImpl(ISpuService spuService,
                          IUnitService unitService,
                          IPriceRuleService priceRuleService) {
        this.spuService = spuService;
        this.unitService = unitService;
        this.priceRuleService = priceRuleService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSku(SkuSaveReqVO req) {
        validate(req, null);
        Sku entity = new Sku();
        copy(req, entity);
        entity.setStatus(req.getStatus() == null ? STATUS_NORMAL : req.getStatus());
        save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSku(Long id, SkuSaveReqVO req) {
        Sku entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "SKU 不存在：" + id);
        }
        validate(req, id);
        copy(req, entity);
        if (req.getStatus() != null) {
            entity.setStatus(req.getStatus());
        }
        updateById(entity);
    }

    @Override
    public void enable(Long id) {
        changeStatus(id, STATUS_NORMAL);
    }

    @Override
    public void disable(Long id) {
        changeStatus(id, STATUS_DISABLED);
    }

    @Override
    public IPage<SkuPageRespVO> pageSku(SkuPageReqVO req) {
        Page<Sku> page = new Page<>(req.getCurrent(), req.getSize());
        LambdaQueryWrapper<Sku> wrapper = new LambdaQueryWrapper<>();
        if (req.getSpuId() != null) {
            wrapper.eq(Sku::getSpuId, req.getSpuId());
        }
        if (req.getStatus() != null) {
            wrapper.eq(Sku::getStatus, req.getStatus());
        }
        if (StringUtils.hasText(req.getKeyword())) {
            String keyword = req.getKeyword();
            wrapper.and(w -> w.like(Sku::getSkuCode, keyword).or().like(Sku::getBarcode, keyword));
        }
        wrapper.orderByDesc(Sku::getUpdatedAt);
        IPage<Sku> result = page(page, wrapper);

        List<SkuPageRespVO> records = new ArrayList<>();
        for (Sku sku : result.getRecords()) {
            SkuPageRespVO vo = new SkuPageRespVO();
            vo.setId(sku.getId());
            vo.setSpuId(sku.getSpuId());
            vo.setSkuCode(sku.getSkuCode());
            vo.setBarcode(sku.getBarcode());
            vo.setSpec(sku.getSpec());
            vo.setBaseUnit(sku.getBaseUnit());
            vo.setPurchaseUnit(sku.getPurchaseUnit());
            vo.setReferencePrice(sku.getReferencePrice());
            vo.setStandardPrice(sku.getStandardPrice());
            vo.setValuationType(sku.getValuationType());
            vo.setStatus(sku.getStatus());
            vo.setUpdatedAt(sku.getUpdatedAt());
            records.add(vo);
        }
        Page<SkuPageRespVO> respPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        respPage.setRecords(records);
        return respPage;
    }

    @Override
    public List<Sku> listBySpu(Long spuId) {
        return list(new LambdaQueryWrapper<Sku>().eq(Sku::getSpuId, spuId).orderByAsc(Sku::getId));
    }

    private void validate(SkuSaveReqVO req, Long excludeId) {
        if (req.getSpuId() == null || spuService.getById(req.getSpuId()) == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "SPU 不存在：" + req.getSpuId());
        }
        if (!StringUtils.hasText(req.getSkuCode())) {
            throw new BizException(ResultCode.PARAM_ERROR, "SKU 编码必填");
        }
        if (baseMapper.existsBySkuCode(req.getSkuCode(), excludeId)) {
            throw new BizException(ResultCode.DATA_CONFLICT, "SKU 编码已存在：" + req.getSkuCode());
        }
        if (StringUtils.hasText(req.getBarcode()) && baseMapper.existsByBarcode(req.getBarcode(), excludeId)) {
            throw new BizException(ResultCode.DATA_CONFLICT, "条码已存在：" + req.getBarcode());
        }
        if (StringUtils.hasText(req.getBaseUnit())) {
            unitService.assertExists(req.getBaseUnit());
        }
        if (StringUtils.hasText(req.getPurchaseUnit())) {
            unitService.assertExists(req.getPurchaseUnit());
        }
        // P3c-A6：参考价必填且 ≥0；标准价选填（留空时采购流程走参考价，PRD PM-08 L503）
        if (req.getReferencePrice() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "参考价必填");
        }
        checkNonNegative("参考价", req.getReferencePrice());
        checkNonNegative("标准价", req.getStandardPrice());
        // 价格规则校验（R-PRD-09）：参考价必校；标准价为空时跳过（选填）
        priceRuleService.validatePrice(PriceRefType.SPU.getValue(), req.getSpuId(), req.getReferencePrice());
        if (req.getStandardPrice() != null) {
            priceRuleService.validatePrice(PriceRefType.SPU.getValue(), req.getSpuId(), req.getStandardPrice());
        }
    }

    private void copy(SkuSaveReqVO req, Sku entity) {
        entity.setSpuId(req.getSpuId());
        entity.setSkuCode(req.getSkuCode());
        entity.setBarcode(req.getBarcode());
        entity.setBaseUnit(req.getBaseUnit());
        entity.setSpec(req.getSpec());
        entity.setPurchaseUnit(req.getPurchaseUnit());
        entity.setReferencePrice(req.getReferencePrice());
        entity.setStandardPrice(req.getStandardPrice());
        entity.setValuationType(req.getValuationType() == null ? ValuationType.BY_PIECE : req.getValuationType());
        entity.setImageFileKey(req.getImageFileKey());
    }

    private void checkNonNegative(String field, BigDecimal value) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(ResultCode.PARAM_ERROR, field + "不可为负");
        }
    }

    private void changeStatus(Long id, ProductStatus status) {
        Sku entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "SKU 不存在：" + id);
        }
        entity.setStatus(status);
        updateById(entity);
    }
}
