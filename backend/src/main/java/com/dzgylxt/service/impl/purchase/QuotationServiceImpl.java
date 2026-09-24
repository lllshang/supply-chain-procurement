package com.dzgylxt.service.impl.purchase;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.catalog.Sku;
import com.dzgylxt.entity.catalog.UnitConversion;
import com.dzgylxt.entity.purchase.Inquiry;
import com.dzgylxt.entity.purchase.InquirySupplier;
import com.dzgylxt.entity.purchase.PurchaseApplyItem;
import com.dzgylxt.entity.purchase.Quotation;
import com.dzgylxt.enums.InquiryStatus;
import com.dzgylxt.enums.QuotationStatus;
import com.dzgylxt.mapper.catalog.SkuMapper;
import com.dzgylxt.mapper.catalog.UnitConversionMapper;
import com.dzgylxt.mapper.purchase.InquiryMapper;
import com.dzgylxt.mapper.purchase.InquirySupplierMapper;
import com.dzgylxt.mapper.purchase.PurchaseApplyItemMapper;
import com.dzgylxt.mapper.purchase.QuotationMapper;
import com.dzgylxt.service.IPriceHistoryService;
import com.dzgylxt.service.IQuotationService;
import com.dzgylxt.vo.purchase.QuotationImportResultVO;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 报价服务实现（设计 §2.3）。
 *
 * <p>批次导入口径（QA #21/#23/#24 修复后）：</p>
 * <ol>
 *   <li><b>模板/导入 ID 均为文本</b>：19 位雪花 ID 超出 Excel double 53 位精度，
 *       模板以字符串写出（文本单元格），导入按文本读再 {@code BigDecimal→Long}
 *       精确解析（同时兼容用户把数值填成数字单元格的场景——此时精度已不可恢复，
 *       由"SKU 不属于该询价"校验兜底拦截）；</li>
 *   <li><b>批次失效按供应商维度</b>：同一供应商新批次落库后仅失效<b>该供应商</b>
 *       的旧有效报价，多供应商有效报价共存（比价不退化）；</li>
 *   <li><b>全有或全无</b>：任一行校验失败则整批不落库（AC④），错误明细在
 *       {@code errors} 列表并附错误 Sheet（行号+原因，base64 xlsx）供下载。</li>
 * </ol>
 */
@Service
public class QuotationServiceImpl extends ServiceImpl<QuotationMapper, Quotation>
        implements IQuotationService {

    private static final String TEMPLATE_VERSION = "p2-quotation-v2";

    @Autowired
    private InquiryMapper inquiryMapper;

    @Autowired
    private InquirySupplierMapper inquirySupplierMapper;

    @Autowired
    private PurchaseApplyItemMapper applyItemMapper;

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private UnitConversionMapper unitConversionMapper;

    @Autowired
    private BusinessNoGenerator businessNoGenerator;

    @Autowired
    private IPriceHistoryService priceHistoryService;

    /** 直接持有 Mapper 用于批量失效旧批次 update 语句。 */
    @Autowired
    private QuotationMapper quotationMapper;

    // ---------------- 模板 ----------------

    @Override
    public byte[] exportTemplate(Long inquiryId) {
        Inquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "询价单不存在：" + inquiryId);
        }
        List<PurchaseApplyItem> items = applyItemMapper.selectList(
                Wrappers.<PurchaseApplyItem>lambdaQuery().eq(PurchaseApplyItem::getApplyId, inquiry.getApplyId()));
        List<TemplateRow> rows = new ArrayList<>();
        for (PurchaseApplyItem item : items) {
            Sku sku = skuMapper.selectById(item.getSkuId());
            TemplateRow row = new TemplateRow();
            // 19 位雪花 ID 必须文本写出（数值单元格 double 精度丢位 → 按模板导入必败，QA #21）
            row.setSupplierId("");
            row.setSkuId(String.valueOf(item.getSkuId()));
            row.setSkuCode(sku == null ? "" : sku.getSkuCode());
            row.setSkuName(sku == null ? "" : sku.getSkuCode());
            row.setPurchaseUnit(item.getPurchaseUnit());
            row.setQty(item.getQtyInPurchaseUnit());
            // 供应商信息位：由供应商填写自己的 supplier_id（文本格式）
            rows.add(row);
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out, TemplateRow.class).sheet("报价单").doWrite(rows);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizException(ResultCode.SYSTEM_ERROR, "模板生成失败");
        }
    }

    // ---------------- 批次导入 ----------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuotationImportResultVO importQuotations(Long inquiryId, MultipartFile file) {
        Inquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "询价单不存在：" + inquiryId);
        }
        if (inquiry.getStatus() != InquiryStatus.PUBLISHED && inquiry.getStatus() != InquiryStatus.CLOSED) {
            throw new BizException(ResultCode.STATUS_INVALID, "仅已发布询价可导入报价");
        }
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "报价文件不能为空");
        }

        // 询价 SKU 集合（来自申请明细）与供应商范围（仅未剔除 invited=1 的有效范围，QA #25 剔除式）
        Set<Long> inquirySkuIds = applyItemMapper.selectList(Wrappers.<PurchaseApplyItem>lambdaQuery()
                        .eq(PurchaseApplyItem::getApplyId, inquiry.getApplyId())).stream()
                .map(PurchaseApplyItem::getSkuId).collect(Collectors.toSet());
        List<InquirySupplier> scope = inquirySupplierMapper.selectList(
                Wrappers.<InquirySupplier>lambdaQuery()
                        .eq(InquirySupplier::getInquiryId, inquiryId)
                        .eq(InquirySupplier::getInvited, 1));
        Set<Long> scopeSupplierIds = scope.stream()
                .map(InquirySupplier::getSupplierId).collect(Collectors.toSet());

        List<TemplateRow> rows;
        try {
            rows = EasyExcel.read(file.getInputStream()).head(TemplateRow.class).sheet().doReadSync();
        } catch (IOException | RuntimeException e) {
            throw new BizException(ResultCode.PARAM_ERROR, "报价文件解析失败：" + e.getMessage());
        }

        QuotationImportResultVO result = new QuotationImportResultVO();
        result.setBatchNo("BJ-" + inquiry.getInquiryNo() + "-"
                + businessNoGenerator.nextSubSeq(inquiry.getInquiryNo(), 2));
        result.setTotal(rows.size());

        LocalDateTime now = LocalDateTime.now();
        List<Quotation> batch = new ArrayList<>();
        Set<Long> quotedSupplierIds = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            TemplateRow row = rows.get(i);
            int lineNo = i + 2; // 表头占第 1 行
            Long supplierId = parseId(row.getSupplierId());
            Long skuId = parseId(row.getSkuId());
            if (supplierId == null || skuId == null
                    || row.getPrice() == null || row.getQty() == null) {
                result.getErrors().add("第" + lineNo + "行：供应商ID/SKU ID/数量/单价均必填");
                result.setFail(result.getFail() + 1);
                continue;
            }
            if (row.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                result.getErrors().add("第" + lineNo + "行：报价单价必须大于 0");
                result.setFail(result.getFail() + 1);
                continue;
            }
            if (!inquirySkuIds.contains(skuId)) {
                result.getErrors().add("第" + lineNo + "行：SKU 不属于该询价：" + skuId);
                result.setFail(result.getFail() + 1);
                continue;
            }
            if (!scopeSupplierIds.contains(supplierId)) {
                result.getErrors().add("第" + lineNo + "行：供应商不在询价范围内：" + supplierId);
                result.setFail(result.getFail() + 1);
                continue;
            }
            // P3c-A2：含税三件套必填校验（PRD L697/L709/L717）——税率 0–13 合法域
            if (row.getTaxRate() == null || row.getFreight() == null || row.getDeliveryDays() == null) {
                result.getErrors().add("第" + lineNo + "行：税率/运费/交期均必填（含税口径）");
                result.setFail(result.getFail() + 1);
                continue;
            }
            if (row.getTaxRate().compareTo(BigDecimal.ZERO) < 0
                    || row.getTaxRate().compareTo(new BigDecimal("13")) > 0) {
                result.getErrors().add("第" + lineNo + "行：税率必须在 0–13 之间：" + row.getTaxRate());
                result.setFail(result.getFail() + 1);
                continue;
            }
            if (row.getFreight().compareTo(BigDecimal.ZERO) < 0 || row.getDeliveryDays() < 0) {
                result.getErrors().add("第" + lineNo + "行：运费/交期不得为负");
                result.setFail(result.getFail() + 1);
                continue;
            }
            // 换算快照：报价单位 → 基本单位（当前生效版本，应用时钟）
            String unit = row.getPurchaseUnit() == null ? "" : row.getPurchaseUnit();
            UnitConversion conv = unit == null || unit.isBlank() ? null
                    : unitConversionMapper.selectCurrentEffective(skuId, unit, now);
            BigDecimal rate = conv == null || conv.getRate() == null ? BigDecimal.ONE : conv.getRate();
            BigDecimal qtyBase = row.getQty().multiply(rate);

            Quotation q = new Quotation();
            q.setInquiryId(inquiryId);
            q.setBatchNo(result.getBatchNo());
            q.setSupplierId(supplierId);
            q.setSkuId(skuId);
            q.setPurchaseUnit(unit);
            q.setQtyInBaseUnit(qtyBase);
            q.setPrice(row.getPrice());
            // P3c-A2：含税三件套落库（金额口径 = 含税单价 × 数量，L711）
            q.setTaxRate(row.getTaxRate());
            q.setFreight(row.getFreight());
            q.setDeliveryDays(row.getDeliveryDays());
            q.setStatus(QuotationStatus.SUBMITTED);
            q.setInvalid(0);
            batch.add(q);
            quotedSupplierIds.add(supplierId);
        }

        // 全有或全无（AC④ / QA #24）：任一行校验失败 → 整批不落库；错误 Sheet（行号+原因）随结果返回
        if (!result.getErrors().isEmpty()) {
            result.setFail(result.getErrors().size());
            result.setSuccess(0);
            result.setErrorSheetBase64(Base64.getEncoder()
                    .encodeToString(buildErrorSheet(result.getErrors())));
            return result;
        }

        if (!batch.isEmpty()) {
            result.setSuccess(batch.size());
            saveBatch(batch);
            // 批次失效按供应商维度（QA #23）：仅失效本次报价供应商的旧有效批次，
            // 多供应商有效报价共存；同供应商重复上传以最新批次为准（幂等）
            for (Long supplierId : quotedSupplierIds) {
                quotationMapper.update(null, Wrappers.<Quotation>update()
                        .eq("inquiry_id", inquiryId)
                        .eq("supplier_id", supplierId)
                        .eq("invalid", 0)
                        .ne("batch_no", result.getBatchNo())
                        .set("invalid", 1));
            }
            // 已报价标记
            for (Long supplierId : quotedSupplierIds) {
                InquirySupplier is = inquirySupplierMapper.selectOne(
                        Wrappers.<InquirySupplier>lambdaQuery()
                                .eq(InquirySupplier::getInquiryId, inquiryId)
                                .eq(InquirySupplier::getSupplierId, supplierId)
                                .last("LIMIT 1"));
                if (is != null && (is.getQuoted() == null || is.getQuoted() == 0)) {
                    is.setQuoted(1);
                    inquirySupplierMapper.updateById(is);
                }
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void accept(Long quotationId) {
        Quotation quotation = getById(quotationId);
        if (quotation == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "报价不存在：" + quotationId);
        }
        if (quotation.getStatus() != QuotationStatus.SUBMITTED) {
            throw new BizException(ResultCode.STATUS_INVALID, "报价当前状态不允许流转：" + quotation.getStatus().getDesc());
        }
        quotation.setStatus(QuotationStatus.ACCEPTED);
        updateById(quotation);
        // 价格库埋点①：报价采纳（P3 §1.4，异常价自动入待审）
        priceHistoryService.record(quotation.getSkuId(), quotation.getSupplierId(),
                quotation.getPrice(), com.dzgylxt.enums.PriceSource.QUOTATION,
                "QUOTATION", quotation.getId(), "报价采纳-" + quotation.getBatchNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long quotationId) {
        transition(quotationId, QuotationStatus.REJECTED);
    }

    @Override
    public List<Quotation> listByInquiry(Long inquiryId, String batchNo) {
        return list(Wrappers.<Quotation>lambdaQuery()
                .eq(Quotation::getInquiryId, inquiryId)
                .eq(Quotation::getInvalid, 0)
                .eq(batchNo != null && !batchNo.isBlank(), Quotation::getBatchNo, batchNo)
                .orderByDesc(Quotation::getId));
    }

    /** 采纳/否决：仅 SUBMITTED 可流转（状态机一次性）。 */
    private void transition(Long quotationId, QuotationStatus target) {
        Quotation quotation = getById(quotationId);
        if (quotation == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "报价不存在：" + quotationId);
        }
        if (quotation.getStatus() != QuotationStatus.SUBMITTED) {
            throw new BizException(ResultCode.STATUS_INVALID, "报价当前状态不允许流转：" + quotation.getStatus().getDesc());
        }
        quotation.setStatus(target);
        updateById(quotation);
    }

    /**
     * 文本/数值兼容的雪花 ID 解析：文本单元格精确（BigDecimal→Long）；
     * 数值单元格因 double 精度可能已失真，解析出的值由业务校验兜底。
     */
    private Long parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim()).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            // 超过 Long 范围或非数字：视为非法 ID
            return null;
        }
    }

    /** 错误 Sheet（行号+原因，单表两列），xlsx 字节。 */
    private byte[] buildErrorSheet(List<String> errors) {
        List<List<String>> head = new ArrayList<>();
        head.add(List.of("行号/原因"));
        List<List<String>> data = new ArrayList<>();
        for (String e : errors) {
            data.add(List.of(e));
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out).head(head).sheet("导入错误").doWrite(data);
            return out.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    /**
     * 报价模板/导入行（EasyExcel 注解列头）。
     *
     * <p>supplierId/skuId 用 <b>String</b>：模板写出为文本单元格（防 19 位 ID
     * double 精度丢位），导入按文本读后经 {@link #parseId} 精确解析。</p>
     */
    @Data
    public static class TemplateRow {
        @com.alibaba.excel.annotation.ExcelProperty("供应商ID")
        private String supplierId;
        @com.alibaba.excel.annotation.ExcelProperty("SKU ID")
        private String skuId;
        @com.alibaba.excel.annotation.ExcelProperty("SKU编码")
        private String skuCode;
        @com.alibaba.excel.annotation.ExcelProperty("SKU名称")
        private String skuName;
        @com.alibaba.excel.annotation.ExcelProperty("报价单位")
        private String purchaseUnit;
        @com.alibaba.excel.annotation.ExcelProperty("数量")
        private BigDecimal qty;
        @com.alibaba.excel.annotation.ExcelProperty("单价(元)")
        private BigDecimal price;
        /** P3c-A2：含税税率 %（合法域 0–13） */
        @com.alibaba.excel.annotation.ExcelProperty("税率(%)")
        private BigDecimal taxRate;
        /** P3c-A2：运费（元） */
        @com.alibaba.excel.annotation.ExcelProperty("运费(元)")
        private BigDecimal freight;
        /** P3c-A2：承诺交期（天） */
        @com.alibaba.excel.annotation.ExcelProperty("交期(天)")
        private Integer deliveryDays;
        @com.alibaba.excel.annotation.ExcelProperty("备注")
        private String remark;
    }
}
