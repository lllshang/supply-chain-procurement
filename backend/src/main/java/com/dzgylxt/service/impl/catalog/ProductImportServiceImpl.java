package com.dzgylxt.service.impl.catalog;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.exception.ExcelDataConvertException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.common.TaskRegistry;
import com.dzgylxt.entity.catalog.ProductCategory;
import com.dzgylxt.entity.catalog.Spu;
import com.dzgylxt.mapper.catalog.ProductCategoryMapper;
import com.dzgylxt.mapper.catalog.SpuMapper;
import com.dzgylxt.service.IProductImportService;
import com.dzgylxt.service.ISkuService;
import com.dzgylxt.service.ISpuService;
import com.dzgylxt.service.IUnitService;
import com.dzgylxt.vo.catalog.ProductExportReqVO;
import com.dzgylxt.vo.catalog.ProductImportPreviewVO;
import com.dzgylxt.vo.catalog.ProductImportRowVO;
import com.dzgylxt.vo.catalog.SkuSaveReqVO;
import com.dzgylxt.vo.catalog.SpuSaveReqVO;
import com.dzgylxt.vo.common.ImportErrorVO;
import com.dzgylxt.vo.common.ImportTaskVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 产品导入导出服务实现（R-PRD-10/11/12）。
 */
@Service
public class ProductImportServiceImpl implements IProductImportService {

    private static final String TEMPLATE_VERSION = "v1";

    private final ISpuService spuService;
    private final ISkuService skuService;
    private final IUnitService unitService;
    private final SpuMapper spuMapper;
    private final ProductCategoryMapper categoryMapper;
    private final TaskRegistry taskRegistry;

    public ProductImportServiceImpl(ISpuService spuService,
                                    ISkuService skuService,
                                    IUnitService unitService,
                                    SpuMapper spuMapper,
                                    ProductCategoryMapper categoryMapper,
                                    TaskRegistry taskRegistry) {
        this.spuService = spuService;
        this.skuService = skuService;
        this.unitService = unitService;
        this.spuMapper = spuMapper;
        this.categoryMapper = categoryMapper;
        this.taskRegistry = taskRegistry;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportTaskVO importSingle(MultipartFile file) {
        return doImport(file, false);
    }

    @Override
    public ProductImportPreviewVO previewMulti(MultipartFile file) {
        List<ProductImportRowVO> rows = readRows(file);
        List<ProductImportRowVO> expanded = new ArrayList<>();
        List<ImportErrorVO> errors = new ArrayList<>();
        Map<String, Long> categoryIndex = categoryCodeIndex();
        for (int i = 0; i < rows.size(); i++) {
            expandAndValidate(rows.get(i), i + 2, categoryIndex, expanded, errors);
        }
        ProductImportPreviewVO preview = new ProductImportPreviewVO();
        preview.setTemplateVersion(TEMPLATE_VERSION);
        preview.setRows(expanded);
        preview.setErrors(errors);
        preview.setValid(errors.isEmpty());
        return preview;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportTaskVO importMulti(MultipartFile file) {
        return doImport(file, true);
    }

    @Override
    public ImportTaskVO export(ProductExportReqVO req, String templateVersion) {
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
        List<Spu> spus = spuMapper.selectList(wrapper);
        Map<Long, String> categoryCodes = categoryMapper.selectList(new LambdaQueryWrapper<ProductCategory>())
                .stream().collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getCode, (a, b) -> a));

        List<ProductImportRowVO> exportRows = new ArrayList<>();
        for (Spu spu : spus) {
            ProductImportRowVO row = new ProductImportRowVO();
            row.setSpuCode(spu.getSpuCode());
            row.setSpuName(spu.getName());
            row.setCategoryCode(categoryCodes.get(spu.getCategoryId()));
            row.setBaseUnit(spu.getBaseUnit());
            row.setDescription(spu.getDescription());
            exportRows.add(row);
        }
        byte[] bytes = writeExcel(exportRows, "商品导出");
        String taskId = taskRegistry.nextId();
        ImportTaskVO task = new ImportTaskVO();
        task.setTaskId(taskId);
        task.setStatus("SUCCESS");
        task.setSuccess(true);
        task.setTotalRows(exportRows.size());
        task.setErrorRows(0);
        task.setFileName("product-export-" + taskId + ".xlsx");
        taskRegistry.putTask(task);
        taskRegistry.putArtifact(taskId, bytes);
        return task;
    }

    @Override
    public ImportTaskVO exportTaskStatus(String taskId) {
        ImportTaskVO task = taskRegistry.getTask(taskId);
        if (task == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "导出任务不存在：" + taskId);
        }
        return task;
    }

    @Override
    public byte[] exportBytes(String taskId) {
        byte[] bytes = taskRegistry.getArtifact(taskId);
        if (bytes == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "导出文件不存在或已过期：" + taskId);
        }
        return bytes;
    }

    // ---------------- 内部实现 ----------------

    private ImportTaskVO doImport(MultipartFile file, boolean multi) {
        List<ProductImportRowVO> rows = readRows(file);
        Map<String, Long> categoryIndex = categoryCodeIndex();
        List<ProductImportRowVO> expanded = new ArrayList<>();
        List<ImportErrorVO> errors = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            if (multi) {
                expandAndValidate(rows.get(i), i + 2, categoryIndex, expanded, errors);
            } else {
                expanded.add(rows.get(i));
                validateRow(rows.get(i), i + 2, categoryIndex, errors);
            }
        }
        ImportTaskVO task = new ImportTaskVO();
        task.setTaskId(taskRegistry.nextId());
        task.setTotalRows(rows.size());
        task.setErrorRows(errors.size());
        task.setErrors(errors);
        if (!errors.isEmpty()) {
            task.setStatus("FAILED");
            task.setSuccess(false);
            taskRegistry.putTask(task);
            return task;
        }
        // 全部校验通过才落库
        for (ProductImportRowVO row : expanded) {
            Long categoryId = categoryIndex.get(row.getCategoryCode());
            SpuSaveReqVO spuReq = new SpuSaveReqVO();
            spuReq.setSpuCode(row.getSpuCode());
            spuReq.setName(row.getSpuName());
            spuReq.setCategoryId(categoryId);
            spuReq.setBaseUnit(row.getBaseUnit());
            spuReq.setDescription(row.getDescription());
            Long spuId = spuService.createSpu(spuReq);

            SkuSaveReqVO skuReq = new SkuSaveReqVO();
            skuReq.setSpuId(spuId);
            skuReq.setSkuCode(row.getSkuCode());
            skuReq.setBarcode(row.getBarcode());
            skuReq.setBaseUnit(row.getBaseUnit());
            skuReq.setSpec(row.getSpec());
            skuReq.setPurchaseUnit(row.getPurchaseUnit());
            skuReq.setReferencePrice(row.getReferencePrice());
            skuReq.setStandardPrice(row.getStandardPrice());
            skuReq.setValuationType(row.getValuationType());
            skuService.createSku(skuReq);
        }
        task.setStatus("SUCCESS");
        task.setSuccess(true);
        taskRegistry.putTask(task);
        return task;
    }

    /** 展开多规格组合并校验。 */
    private void expandAndValidate(ProductImportRowVO row, int rowNo, Map<String, Long> categoryIndex,
                                   List<ProductImportRowVO> expanded, List<ImportErrorVO> errors) {
        List<String> specs = expandSpecs(row.getSpecCombos());
        if (specs.isEmpty()) {
            expanded.add(row);
            validateRow(row, rowNo, categoryIndex, errors);
            return;
        }
        for (int i = 0; i < specs.size(); i++) {
            ProductImportRowVO variant = copyRow(row);
            variant.setSpec(specs.get(i));
            if (specs.size() > 1 && StringUtils.hasText(row.getSkuCode())) {
                variant.setSkuCode(row.getSkuCode() + "-" + (i + 1));
            }
            expanded.add(variant);
            validateRow(variant, rowNo, categoryIndex, errors);
        }
    }

    /** 单行校验（字段/类型/业务规则），错误定位到行/列。 */
    private void validateRow(ProductImportRowVO row, int rowNo, Map<String, Long> categoryIndex,
                             List<ImportErrorVO> errors) {
        if (!StringUtils.hasText(row.getSpuCode())) {
            errors.add(new ImportErrorVO(rowNo, "SPU编码", "SPU 编码必填"));
        }
        if (!StringUtils.hasText(row.getSpuName())) {
            errors.add(new ImportErrorVO(rowNo, "SPU名称", "SPU 名称必填"));
        }
        if (!StringUtils.hasText(row.getCategoryCode()) || !categoryIndex.containsKey(row.getCategoryCode())) {
            errors.add(new ImportErrorVO(rowNo, "品类编码", "品类不存在：" + row.getCategoryCode()));
        }
        if (!StringUtils.hasText(row.getSkuCode())) {
            errors.add(new ImportErrorVO(rowNo, "SKU编码", "SKU 编码必填"));
        }
        if (StringUtils.hasText(row.getBaseUnit())) {
            try {
                unitService.assertExists(row.getBaseUnit());
            } catch (BizException e) {
                errors.add(new ImportErrorVO(rowNo, "基本单位", e.getMessage()));
            }
        }
        if (StringUtils.hasText(row.getPurchaseUnit())) {
            try {
                unitService.assertExists(row.getPurchaseUnit());
            } catch (BizException e) {
                errors.add(new ImportErrorVO(rowNo, "采购单位", e.getMessage()));
            }
        }
        if (row.getReferencePrice() != null && row.getReferencePrice().compareTo(BigDecimal.ZERO) < 0) {
            errors.add(new ImportErrorVO(rowNo, "参考价", "参考价不可为负"));
        }
        if (row.getStandardPrice() != null && row.getStandardPrice().compareTo(BigDecimal.ZERO) < 0) {
            errors.add(new ImportErrorVO(rowNo, "标准价", "标准价不可为负"));
        }
    }

    /** 解析规格组合串，如 "颜色:红,绿;尺寸:S,M" → ["颜色=红;尺寸=S","颜色=红;尺寸=M",...]。 */
    private List<String> expandSpecs(String specCombos) {
        List<String> result = new ArrayList<>();
        if (!StringUtils.hasText(specCombos)) {
            return result;
        }
        List<List<String>> dimensions = new ArrayList<>();
        List<String> dimensionNames = new ArrayList<>();
        for (String dim : specCombos.split(";")) {
            if (!StringUtils.hasText(dim)) {
                continue;
            }
            String[] kv = dim.split(":", 2);
            if (kv.length < 2) {
                continue;
            }
            dimensionNames.add(kv[0].trim());
            List<String> values = new ArrayList<>();
            for (String v : kv[1].split(",")) {
                if (StringUtils.hasText(v)) {
                    values.add(v.trim());
                }
            }
            dimensions.add(values);
        }
        if (dimensions.isEmpty()) {
            return result;
        }
        List<String> current = new ArrayList<>();
        current.add("");
        for (int d = 0; d < dimensions.size(); d++) {
            List<String> next = new ArrayList<>();
            for (String prefix : current) {
                for (String value : dimensions.get(d)) {
                    String part = dimensionNames.get(d) + "=" + value;
                    next.add(prefix.isEmpty() ? part : prefix + ";" + part);
                }
            }
            current = next;
        }
        result.addAll(current);
        return result;
    }

    private ProductImportRowVO copyRow(ProductImportRowVO source) {
        ProductImportRowVO target = new ProductImportRowVO();
        target.setSpuCode(source.getSpuCode());
        target.setSpuName(source.getSpuName());
        target.setCategoryCode(source.getCategoryCode());
        target.setBaseUnit(source.getBaseUnit());
        target.setPurchaseUnit(source.getPurchaseUnit());
        target.setSkuCode(source.getSkuCode());
        target.setBarcode(source.getBarcode());
        target.setSpec(source.getSpec());
        target.setSpecCombos(source.getSpecCombos());
        target.setReferencePrice(source.getReferencePrice());
        target.setStandardPrice(source.getStandardPrice());
        target.setValuationType(source.getValuationType());
        target.setDescription(source.getDescription());
        return target;
    }

    private List<ProductImportRowVO> readRows(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "上传文件为空");
        }
        try {
            return EasyExcel.read(file.getInputStream()).head(ProductImportRowVO.class).sheet().doReadSync();
        } catch (ExcelDataConvertException e) {
            throw new BizException(ResultCode.BIZ_ERROR,
                    "第 " + (e.getRowIndex() + 1) + " 行第 " + (e.getColumnIndex() + 1) + " 列类型错误");
        } catch (IOException e) {
            throw new BizException(ResultCode.BIZ_ERROR, "文件解析失败：" + e.getMessage());
        }
    }

    private Map<String, Long> categoryCodeIndex() {
        return categoryMapper.selectList(new LambdaQueryWrapper<ProductCategory>())
                .stream().collect(Collectors.toMap(ProductCategory::getCode, ProductCategory::getId, (a, b) -> a));
    }

    private byte[] writeExcel(List<ProductImportRowVO> rows, String sheetName) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out, ProductImportRowVO.class).sheet(sheetName).doWrite(rows);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizException(ResultCode.BIZ_ERROR, "导出失败：" + e.getMessage());
        }
    }
}
