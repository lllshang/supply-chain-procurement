package com.dzgylxt.service.impl.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.enums.ProductStatus;
import com.dzgylxt.entity.catalog.ProductCategory;
import com.dzgylxt.entity.catalog.Spu;
import com.dzgylxt.mapper.catalog.ProductCategoryMapper;
import com.dzgylxt.mapper.catalog.SpuMapper;
import com.dzgylxt.service.IProductCategoryService;
import com.dzgylxt.service.ISpuService;
import com.dzgylxt.service.IUnitService;
import com.dzgylxt.vo.catalog.SpuPageReqVO;
import com.dzgylxt.vo.catalog.SpuPageRespVO;
import com.dzgylxt.vo.catalog.SpuSaveReqVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 商品 SPU 服务实现。 */
@Service
public class SpuServiceImpl extends ServiceImpl<SpuMapper, Spu> implements ISpuService {

    private static final ProductStatus STATUS_NORMAL = ProductStatus.NORMAL;
    private static final ProductStatus STATUS_DISABLED = ProductStatus.DISABLED;

    private final IProductCategoryService categoryService;
    private final IUnitService unitService;
    private final ProductCategoryMapper categoryMapper;

    public SpuServiceImpl(IProductCategoryService categoryService,
                          IUnitService unitService,
                          ProductCategoryMapper categoryMapper) {
        this.categoryService = categoryService;
        this.unitService = unitService;
        this.categoryMapper = categoryMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSpu(SpuSaveReqVO req) {
        if (!StringUtils.hasText(req.getSpuCode())) {
            throw new BizException(ResultCode.PARAM_ERROR, "SPU 编码必填");
        }
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException(ResultCode.PARAM_ERROR, "SPU 名称必填");
        }
        if (baseMapper.existsByCode(req.getSpuCode(), null)) {
            throw new BizException(ResultCode.DATA_CONFLICT, "SPU 编码已存在：" + req.getSpuCode());
        }
        categoryService.assertLeaf(req.getCategoryId());
        if (StringUtils.hasText(req.getBaseUnit())) {
            unitService.assertExists(req.getBaseUnit());
        }
        Spu entity = new Spu();
        entity.setSpuCode(req.getSpuCode());
        entity.setName(req.getName());
        entity.setCategoryId(req.getCategoryId());
        entity.setSpec(req.getSpec());
        entity.setBaseUnit(req.getBaseUnit());
        entity.setImageFileKey(req.getImageFileKey());
        entity.setDescription(req.getDescription());
        entity.setRemark(req.getRemark());
        entity.setStatus(req.getStatus() == null ? STATUS_NORMAL : req.getStatus());
        save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSpu(Long id, SpuSaveReqVO req) {
        Spu entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "SPU 不存在：" + id);
        }
        if (StringUtils.hasText(req.getSpuCode())
                && !req.getSpuCode().equals(entity.getSpuCode())
                && baseMapper.existsByCode(req.getSpuCode(), id)) {
            throw new BizException(ResultCode.DATA_CONFLICT, "SPU 编码已存在：" + req.getSpuCode());
        }
        if (req.getCategoryId() != null && !req.getCategoryId().equals(entity.getCategoryId())) {
            categoryService.assertLeaf(req.getCategoryId());
            entity.setCategoryId(req.getCategoryId());
        }
        if (StringUtils.hasText(req.getBaseUnit()) && !req.getBaseUnit().equals(entity.getBaseUnit())) {
            unitService.assertExists(req.getBaseUnit());
            entity.setBaseUnit(req.getBaseUnit());
        }
        if (StringUtils.hasText(req.getSpuCode())) {
            entity.setSpuCode(req.getSpuCode());
        }
        if (StringUtils.hasText(req.getName())) {
            entity.setName(req.getName());
        }
        entity.setSpec(req.getSpec());
        entity.setImageFileKey(req.getImageFileKey());
        entity.setDescription(req.getDescription());
        entity.setRemark(req.getRemark());
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
    public IPage<SpuPageRespVO> pageSpu(SpuPageReqVO req) {
        Page<Spu> page = new Page<>(req.getCurrent(), req.getSize());
        LambdaQueryWrapper<Spu> wrapper = new LambdaQueryWrapper<>();
        if (req.getCategoryId() != null) {
            wrapper.eq(Spu::getCategoryId, req.getCategoryId());
        }
        if (req.getStatus() != null) {
            wrapper.eq(Spu::getStatus, req.getStatus());
        }
        if (StringUtils.hasText(req.getKeyword())) {
            String keyword = req.getKeyword();
            wrapper.and(w -> w.like(Spu::getSpuCode, keyword).or().like(Spu::getName, keyword));
        }
        wrapper.orderByDesc(Spu::getUpdatedAt);
        IPage<Spu> result = page(page, wrapper);

        List<Long> categoryIds = result.getRecords().stream()
                .map(Spu::getCategoryId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> nameMap = categoryIds.isEmpty() ? Map.of()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName, (a, b) -> a));

        List<SpuPageRespVO> records = new ArrayList<>();
        for (Spu spu : result.getRecords()) {
            SpuPageRespVO vo = new SpuPageRespVO();
            vo.setId(spu.getId());
            vo.setSpuCode(spu.getSpuCode());
            vo.setName(spu.getName());
            vo.setCategoryId(spu.getCategoryId());
            vo.setCategoryName(nameMap.get(spu.getCategoryId()));
            vo.setBaseUnit(spu.getBaseUnit());
            vo.setImageFileKey(spu.getImageFileKey());
            vo.setStatus(spu.getStatus());
            vo.setUpdatedAt(spu.getUpdatedAt());
            records.add(vo);
        }
        Page<SpuPageRespVO> respPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        respPage.setRecords(records);
        return respPage;
    }

    private void changeStatus(Long id, ProductStatus status) {
        Spu entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "SPU 不存在：" + id);
        }
        entity.setStatus(status);
        updateById(entity);
    }
}
