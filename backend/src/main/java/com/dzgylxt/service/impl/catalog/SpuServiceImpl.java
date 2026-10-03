package com.dzgylxt.service.impl.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.enums.ItemType;
import com.dzgylxt.enums.ProductStatus;
import com.dzgylxt.entity.catalog.ProductCategory;
import com.dzgylxt.entity.catalog.Spu;
import com.dzgylxt.mapper.catalog.ProductCategoryMapper;
import com.dzgylxt.mapper.catalog.SpuMapper;
import com.dzgylxt.service.IProductCategoryService;
import com.dzgylxt.service.ISpuService;
import com.dzgylxt.common.storage.FileStorage;
import com.dzgylxt.entity.catalog.Sku;
import com.dzgylxt.entity.catalog.Unit;
import com.dzgylxt.entity.catalog.UnitConversion;
import com.dzgylxt.mapper.catalog.SkuMapper;
import com.dzgylxt.mapper.catalog.UnitConversionMapper;
import com.dzgylxt.service.IUnitService;
import com.dzgylxt.vo.catalog.SpuPageReqVO;
import com.dzgylxt.vo.catalog.SpuPageRespVO;
import com.dzgylxt.vo.catalog.SpuSaveReqVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 商品 SPU 服务实现。 */
@Slf4j
@Service
public class SpuServiceImpl extends ServiceImpl<SpuMapper, Spu> implements ISpuService {

    private static final ProductStatus STATUS_NORMAL = ProductStatus.NORMAL;
    private static final ProductStatus STATUS_DISABLED = ProductStatus.DISABLED;

    private final IProductCategoryService categoryService;
    private final IUnitService unitService;
    private final ProductCategoryMapper categoryMapper;
    private final SkuMapper skuMapper;
    private final UnitConversionMapper unitConversionMapper;
    private final FileStorage fileStorage;

    public SpuServiceImpl(IProductCategoryService categoryService,
                          IUnitService unitService,
                          ProductCategoryMapper categoryMapper,
                          SkuMapper skuMapper,
                          UnitConversionMapper unitConversionMapper,
                          FileStorage fileStorage) {
        this.categoryService = categoryService;
        this.unitService = unitService;
        this.categoryMapper = categoryMapper;
        this.skuMapper = skuMapper;
        this.unitConversionMapper = unitConversionMapper;
        this.fileStorage = fileStorage;
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
        entity.setItemType(req.getItemType() == null ? ItemType.MATERIAL : req.getItemType());
        entity.setPackType(req.getPackType() == null ? 0 : req.getPackType());
        entity.setSpec(req.getSpec());
        entity.setBaseUnit(req.getBaseUnit());
        entity.setImageFileKey(req.getImageFileKey());
        entity.setDescription(req.getDescription());
        entity.setTaxRate(req.getTaxRate());
        entity.setRemark(req.getRemark());
        entity.setStatus(req.getStatus() == null ? STATUS_NORMAL : req.getStatus());
        // ===== 编辑对话框全量对齐原型 =====
        applySpecFields(entity, req);
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
        if (req.getItemType() != null) {
            entity.setItemType(req.getItemType());
        }
        if (req.getPackType() != null) {
            entity.setPackType(req.getPackType());
        }
        applySpecFields(entity, req);
        if (StringUtils.hasText(req.getSpuCode())) {
            entity.setSpuCode(req.getSpuCode());
        }
        if (StringUtils.hasText(req.getName())) {
            entity.setName(req.getName());
        }
        entity.setSpec(req.getSpec());
        entity.setImageFileKey(req.getImageFileKey());
        entity.setDescription(req.getDescription());
        entity.setTaxRate(req.getTaxRate());
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
        if (req.getItemType() != null) {
            wrapper.eq(Spu::getItemType, req.getItemType());
        }
        if (StringUtils.hasText(req.getSpecMode())) {
            // 规格类型筛选：单/多规格由 SKU 数量推导，固定混色箱由 spu.pack_type 决定
            String mode = req.getSpecMode();
            if ("single".equals(mode)) {
                wrapper.inSql(Spu::getId,
                        "SELECT spu_id FROM sku WHERE deleted = 0 GROUP BY spu_id HAVING COUNT(*) = 1");
            } else if ("multiple".equals(mode)) {
                wrapper.inSql(Spu::getId,
                                "SELECT spu_id FROM sku WHERE deleted = 0 GROUP BY spu_id HAVING COUNT(*) > 1")
                        .ne(Spu::getPackType, 1);
            } else if ("mixed".equals(mode)) {
                wrapper.eq(Spu::getPackType, 1);
            }
        }
        if (StringUtils.hasText(req.getKeyword())) {
            String keyword = req.getKeyword();
            wrapper.and(w -> w.like(Spu::getSpuCode, keyword).or().like(Spu::getName, keyword));
        }
        wrapper.orderByDesc(Spu::getUpdatedAt);
        IPage<Spu> result = page(page, wrapper);

        // ---- 三级品类路径（叶子 + 祖先） ----
        List<Long> leafIds = result.getRecords().stream()
                .map(Spu::getCategoryId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> categoryNameMap = Map.of();
        Map<Long, ProductCategory> catMap = Map.of();
        if (!leafIds.isEmpty()) {
            List<ProductCategory> leaves = categoryMapper.selectBatchIds(leafIds);
            Set<Long> allCatIds = new HashSet<>(leafIds);
            for (ProductCategory c : leaves) {
                if (StringUtils.hasText(c.getTreePath())) {
                    for (String p : c.getTreePath().split("/")) {
                        if (!p.isEmpty()) {
                            allCatIds.add(Long.parseLong(p));
                        }
                    }
                }
            }
            List<ProductCategory> allCats = categoryMapper.selectBatchIds(allCatIds);
            catMap = allCats.stream()
                    .collect(Collectors.toMap(ProductCategory::getId, c -> c, (a, b) -> a));
            categoryNameMap = leaves.stream()
                    .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getName, (a, b) -> a));
        }

        // ---- SKU 聚合（按 spuId 批量查询） ----
        List<Long> spuIds = result.getRecords().stream().map(Spu::getId).toList();
        Map<Long, List<Sku>> skuMap = Map.of();
        if (!spuIds.isEmpty()) {
            List<Sku> allSkus = skuMapper.selectList(
                    new LambdaQueryWrapper<Sku>().in(Sku::getSpuId, spuIds));
            skuMap = allSkus.stream().collect(Collectors.groupingBy(Sku::getSpuId));
        }
        LocalDateTime now = LocalDateTime.now();

        // ---- 单位显示名（unit.code → unit.name，供列表展示中文单位） ----
        Map<String, String> unitNameMap = Map.of();
        List<Unit> units = unitService.list();
        if (!units.isEmpty()) {
            unitNameMap = units.stream()
                    .filter(u -> StringUtils.hasText(u.getCode()))
                    .collect(Collectors.toMap(Unit::getCode,
                            u -> StringUtils.hasText(u.getName()) ? u.getName() : u.getCode(),
                            (a, b) -> a));
        }

        List<SpuPageRespVO> records = new ArrayList<>();
        for (Spu spu : result.getRecords()) {
            SpuPageRespVO vo = new SpuPageRespVO();
            vo.setId(spu.getId());
            vo.setSpuCode(spu.getSpuCode());
            vo.setName(spu.getName());
            vo.setCategoryId(spu.getCategoryId());
            vo.setItemType(spu.getItemType());
            vo.setPackType(spu.getPackType());
            vo.setCategoryName(categoryNameMap.get(spu.getCategoryId()));
            vo.setCategoryPath(buildCategoryPath(spu.getCategoryId(), catMap));
            vo.setBaseUnit(spu.getBaseUnit());
            vo.setBaseUnitName(unitDisplayName(unitNameMap, spu.getBaseUnit()));
            vo.setSpecification(spu.getSpec());
            vo.setImageFileKey(spu.getImageFileKey());
            vo.setImageUrl(resolveImageUrl(spu.getImageFileKey()));
            vo.setStatus(spu.getStatus());
            vo.setDescription(spu.getDescription());
            vo.setTaxRate(spu.getTaxRate());
            vo.setUpdatedAt(spu.getUpdatedAt());

            List<Sku> skus = skuMap.getOrDefault(spu.getId(), List.of());
            vo.setSkuCount(skus.size());
            BigDecimal priceMin = null;
            BigDecimal priceMax = null;
            String purchaseUnit = null;
            for (Sku s : skus) {
                if (s.getStandardPrice() != null) {
                    if (priceMin == null || s.getStandardPrice().compareTo(priceMin) < 0) {
                        priceMin = s.getStandardPrice();
                    }
                    if (priceMax == null || s.getStandardPrice().compareTo(priceMax) > 0) {
                        priceMax = s.getStandardPrice();
                    }
                }
                if (purchaseUnit == null && StringUtils.hasText(s.getPurchaseUnit())) {
                    purchaseUnit = s.getPurchaseUnit();
                }
            }
            vo.setStandardPriceMin(priceMin);
            vo.setStandardPriceMax(priceMax);
            vo.setPurchaseUnit(purchaseUnit);
            vo.setPurchaseUnitName(unitDisplayName(unitNameMap, purchaseUnit));
            if (!skus.isEmpty()) {
                // 单规格：SPU 级规格缺失时回退用该唯一 SKU 的规格（对齐原型"规格"列显示规格值）
                if (skus.size() == 1 && !StringUtils.hasText(vo.getSpecification())) {
                    vo.setSpecification(skus.get(0).getSpec());
                }
            }
            // 单位换算文本：单规格/混色箱取 SPU 级系数，多规格取首个 SKU 级系数；回退到单位换算表
            String conversion = buildInlineConversionText(spu, skus, unitNameMap);
            if (conversion == null && !skus.isEmpty()) {
                conversion = buildConversionText(skus.get(0), now, unitNameMap);
            }
            vo.setUnitConversion(conversion);
            records.add(vo);
        }
        Page<SpuPageRespVO> respPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        respPage.setRecords(records);
        return respPage;
    }

    @Override
    public long countTotalSkus() {
        return skuMapper.selectCount(new LambdaQueryWrapper<Sku>());
    }

    private String buildCategoryPath(Long categoryId, Map<Long, ProductCategory> catMap) {
        if (categoryId == null) return null;
        ProductCategory leaf = catMap.get(categoryId);
        if (leaf == null) return null;
        List<String> names = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        if (StringUtils.hasText(leaf.getTreePath())) {
            for (String p : leaf.getTreePath().split("/")) {
                if (p.isEmpty()) continue;
                Long id = Long.parseLong(p);
                if (seen.add(id)) {
                    ProductCategory anc = catMap.get(id);
                    if (anc != null) names.add(anc.getName());
                }
            }
        }
        // treePath 可能含自身（/1/2/3）也可能只含祖先（/1/2），去重后仅在未包含时补叶子名
        if (seen.add(categoryId)) {
            names.add(leaf.getName());
        }
        return names.isEmpty() ? leaf.getName() : String.join(" / ", names);
    }

    private String resolveImageUrl(String fileKey) {
        if (!StringUtils.hasText(fileKey)) return null;
        try {
            return fileStorage.getUrl(fileKey);
        } catch (RuntimeException e) {
            log.debug("产品主图 URL 解析失败（存储可能不可用）: {}", e.getMessage());
            return null;
        }
    }

    /** 由内联字段推导单位换算文本：单规格/混色箱用 SPU 级系数，多规格用首个 SKU 级系数。系数为 1 或单位相同则不展示。 */
    private String buildInlineConversionText(Spu spu, List<Sku> skus, Map<String, String> unitNameMap) {
        String pu;
        String bu;
        Integer factor;
        if ("multiple".equals(spu.getSpecificationMode()) && !skus.isEmpty()) {
            Sku rep = skus.get(0);
            pu = rep.getPurchaseUnit();
            bu = rep.getBaseUnit();
            factor = rep.getUnitConversionFactor();
        } else {
            pu = spu.getPurchaseUnit();
            bu = spu.getBaseUnit();
            factor = spu.getUnitConversionFactor();
        }
        if (!StringUtils.hasText(pu) || !StringUtils.hasText(bu) || pu.equals(bu)) {
            return null;
        }
        if (factor == null || factor <= 1) {
            return null;
        }
        return "1" + unitDisplayName(unitNameMap, pu) + " = " + factor + unitDisplayName(unitNameMap, bu);
    }

    private String buildConversionText(Sku sku, LocalDateTime now, Map<String, String> unitNameMap) {
        String pu = unitDisplayName(unitNameMap, sku.getPurchaseUnit());
        String bu = unitDisplayName(unitNameMap, sku.getBaseUnit());
        UnitConversion c = unitConversionMapper.selectCurrentEffective(sku.getId(), sku.getPurchaseUnit(), now);
        if (c != null && sku.getBaseUnit().equals(c.getToUnit()) && c.getRate() != null) {
            return "1" + pu + " = " + formatRate(c.getRate()) + bu;
        }
        UnitConversion c2 = unitConversionMapper.selectCurrentEffective(sku.getId(), sku.getBaseUnit(), now);
        if (c2 != null && sku.getPurchaseUnit().equals(c2.getToUnit())
                && c2.getRate() != null && c2.getRate().compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal inv = BigDecimal.ONE.divide(c2.getRate(), 6, RoundingMode.HALF_UP);
            return "1" + pu + " = " + formatRate(inv) + bu;
        }
        return null;
    }

    /** 单位显示名：优先 unit.name，未命中回退 unit.code。 */
    private String unitDisplayName(Map<String, String> unitNameMap, String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return unitNameMap.getOrDefault(code, code);
    }

    private String formatRate(BigDecimal rate) {
        return rate.stripTrailingZeros().toPlainString();
    }

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /** 将编辑对话框全量字段（含 JSON 结构的混色箱子件/多规格属性）映射到 SPU 实体。 */
    private void applySpecFields(Spu entity, SpuSaveReqVO req) {
        entity.setBarcode(req.getBarcode());
        entity.setMeasurementType(req.getMeasurementType());
        entity.setPurchaseUnit(req.getPurchaseUnit());
        if (StringUtils.hasText(req.getSpecificationMode())) {
            entity.setSpecificationMode(req.getSpecificationMode());
            entity.setPackType("mixed".equals(req.getSpecificationMode()) ? 1 : 0);
        }
        entity.setUnitConversionFactor(req.getUnitConversionFactor());
        entity.setStandardPrice(req.getStandardPrice());
        entity.setReferencePrice(req.getReferencePrice());
        entity.setMixedPackComponents(toJson(req.getMixedPackComponents()));
        entity.setSpecAttributes(toJson(req.getSpecAttributes()));
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return JSON_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("SPU 扩展字段 JSON 序列化失败: {}", e.getMessage());
            return null;
        }
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
