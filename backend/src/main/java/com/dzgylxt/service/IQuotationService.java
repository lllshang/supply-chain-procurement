package com.dzgylxt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.purchase.Quotation;
import com.dzgylxt.vo.purchase.QuotationImportResultVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 报价服务（设计 §2.3：模板导出 / 批次导入 / 采纳否决）。 */
public interface IQuotationService extends IService<Quotation> {

    /** 报价包模板（含询价明细 + 供应商信息位 + templateVersion）。 */
    byte[] exportTemplate(Long inquiryId);

    /**
     * 批次导入：按"供应商×明细"落行；价格&gt;0 / SKU 属于该询价 / 供应商在范围内；
     * 换算快照落 qty_in_base_unit；新批次落库后旧批次 invalid=1；错误行汇总返回。
     */
    QuotationImportResultVO importQuotations(Long inquiryId, MultipartFile file);

    /** B4：按批次号取回导入错误 Sheet 的 xlsx 字节（base64，源自 Redis 暂存；不存在返回 null）。 */
    String getErrorSheetBase64(String batchNo);

    /** 比价采纳：SUBMITTED → ACCEPTED。 */
    void accept(Long quotationId);

    /** 比价否决：SUBMITTED → REJECTED。 */
    void reject(Long quotationId);

    /** 按询价查有效报价（invalid=0，可按批次过滤）。 */
    List<Quotation> listByInquiry(Long inquiryId, String batchNo);
}
