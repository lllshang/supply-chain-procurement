package com.dzgylxt.controller.purchase;

import com.dzgylxt.common.R;
import com.dzgylxt.controller.BaseController;
import com.dzgylxt.entity.purchase.Quotation;
import com.dzgylxt.service.IQuotationService;
import com.dzgylxt.vo.purchase.QuotationImportResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.dzgylxt.common.SecurityConstants;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.List;

import com.dzgylxt.common.BizException;
import com.dzgylxt.common.ResultCode;

/** 报价管理（设计 §5.2：/api/v1/quotations + 询价下的导入端点）。 */
@RestController
@RequestMapping("/api/v1/quotations")
public class QuotationController extends BaseController<IQuotationService, Quotation> {

    @Autowired
    private IQuotationService quotationService;

    /** 报价包模板下载（含询价明细 + 供应商信息位 + templateVersion）。 */
    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/template")
    public ResponseEntity<byte[]> template(@RequestParam Long inquiryId) {
        return xlsx(quotationService.exportTemplate(inquiryId), "quotation-template-" + inquiryId + ".xlsx");
    }

    /** 有效报价列表（invalid=0，可按批次过滤）。 */
    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping
    public R<List<Quotation>> list(@RequestParam Long inquiryId,
                                   @RequestParam(required = false) String batchNo) {
        return R.ok(quotationService.listByInquiry(inquiryId, batchNo));
    }

    /** 批次导入（询价维度端点，设计 §5.2 /inquiries/{id}/quotations/import）。 */
    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping("/import")
    public R<QuotationImportResultVO> importQuotations(@RequestParam Long inquiryId,
                                                       @RequestParam("file") MultipartFile file) {
        return R.ok(quotationService.importQuotations(inquiryId, file));
    }

    /** 错误 Sheet 独立文件流下载（B4：替代内联 base64，按批次号从 Redis 取 xlsx 字节流式返回）。 */
    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/import/error-sheet/{batchNo}")
    public ResponseEntity<byte[]> errorSheet(@PathVariable String batchNo) {
        String b64 = quotationService.getErrorSheetBase64(batchNo);
        if (b64 == null || b64.isBlank()) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "错误 Sheet 不存在或已过期（请重新导入）");
        }
        byte[] bytes = Base64.getDecoder().decode(b64);
        return xlsx(bytes, "quotation-error-sheet-" + batchNo + ".xlsx");
    }

    /** 比价采纳。 */
    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping("/{id}/accept")
    public R<Boolean> accept(@PathVariable Long id) {
        quotationService.accept(id);
        return R.ok(true);
    }

    /** 比价否决。 */
    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping("/{id}/reject")
    public R<Boolean> reject(@PathVariable Long id) {
        quotationService.reject(id);
        return R.ok(true);
    }

    private ResponseEntity<byte[]> xlsx(byte[] bytes, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
