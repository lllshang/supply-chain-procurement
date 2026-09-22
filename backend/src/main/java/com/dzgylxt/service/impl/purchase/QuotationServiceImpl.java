package com.dzgylxt.service.impl.purchase;

import cn.hutool.json.JSONUtil;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 报价服务实现（设计 §2.3）。
 *
 * <p>批次导入幂等：同一批次号维度整体失效旧批次（新批次落库后同询价旧批次
 * {@code invalid=1}，重复上传同一文件也只保留最新批次有效行）。</p>
 */
@Service
public class QuotationServiceImpl extends ServiceImpl<QuotationMapper, Quotation>
        implements IQuotationService {

    private static final String TEMPLATE_VERSION = "p2-quotation-v1";

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
            row.setSkuId(item.getSkuId());
            row.setSkuCode(sku == null ? "" : sku.getSkuCode());
            row.setSkuName(sku == null ? "" : sku.getSkuCode());
            row.setPurchaseUnit(item.getPurchaseUnit());
            row.setQty(item.getQtyInPurchaseUnit());
            // 供应商信息位：导入时按"供应商名称+供应商ID"识别（空模板由供应商填写）
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

        // 询价 SKU 集合（来自申请明细）与供应商范围
        Set<Long> inquirySkuIds = applyItemMapper.selectList(Wrappers.<PurchaseApplyItem>lambdaQuery()
                        .eq(PurchaseApplyItem::getApplyId, inquiry.getApplyId())).stream()
                .map(PurchaseApplyItem::getSkuId).collect(Collectors.toSet());
        List<InquirySupplier> scope = inquirySupplierMapper.selectList(
                Wrappers.<InquirySupplier>lambdaQuery().eq(InquirySupplier::getInquiryId, inquiryId));
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
            if (row.getSupplierId() == null || row.getSkuId() == null
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
            if (!inquirySkuIds.contains(row.getSkuId())) {
                result.getErrors().add("第" + lineNo + "行：SKU 不属于该询价：" + row.getSkuId());
                result.setFail(result.getFail() + 1);
                continue;
            }
            if (!scopeSupplierIds.contains(row.getSupplierId())) {
                result.getErrors().add("第" + lineNo + "行：供应商不在询价范围内：" + row.getSupplierId());
                result.setFail(result.getFail() + 1);
                continue;
            }
            // 换算快照：报价单位 → 基本单位（当前生效版本，应用时钟）
            String unit = row.getPurchaseUnit() == null ? "" : row.getPurchaseUnit();
            UnitConversion conv = unit == null || unit.isBlank() ? null
                    : unitConversionMapper.selectCurrentEffective(row.getSkuId(), unit, now);
            BigDecimal rate = conv == null || conv.getRate() == null ? BigDecimal.ONE : conv.getRate();
            BigDecimal qtyBase = row.getQty().multiply(rate);

            Quotation q = new Quotation();
            q.setInquiryId(inquiryId);
            q.setBatchNo(result.getBatchNo());
            q.setSupplierId(row.getSupplierId());
            q.setSkuId(row.getSkuId());
            q.setPurchaseUnit(unit);
            q.setQtyInBaseUnit(qtyBase);
            q.setPrice(row.getPrice());
            q.setStatus(QuotationStatus.SUBMITTED);
            q.setInvalid(0);
            batch.add(q);
            quotedSupplierIds.add(row.getSupplierId());
            result.setSuccess(result.getSuccess() + 1);
        }

        if (!batch.isEmpty()) {
            // 新批次落库后旧批次整体失效（幂等：重复上传以最新批次为准）
            saveBatch(batch);
            quotationMapper.update(null, Wrappers.<Quotation>update()
                    .eq("inquiry_id", inquiryId)
                    .eq("invalid", 0)
                    .ne("batch_no", result.getBatchNo())
                    .set("invalid", 1));
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
        transition(quotationId, QuotationStatus.ACCEPTED);
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

    /** 报价模板/导入行（EasyExcel 注解列头）。 */
    @Data
    public static class TemplateRow {
        @com.alibaba.excel.annotation.ExcelProperty("供应商ID")
        private Long supplierId;
        @com.alibaba.excel.annotation.ExcelProperty("SKU ID")
        private Long skuId;
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
        @com.alibaba.excel.annotation.ExcelProperty("备注")
        private String remark;
    }
}
