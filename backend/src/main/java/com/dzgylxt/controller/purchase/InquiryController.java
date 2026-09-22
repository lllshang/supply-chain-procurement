package com.dzgylxt.controller.purchase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dzgylxt.common.PageResult;
import com.dzgylxt.common.R;
import com.dzgylxt.controller.BaseController;
import com.dzgylxt.entity.purchase.Inquiry;
import com.dzgylxt.entity.purchase.InquirySupplier;
import com.dzgylxt.service.IInquiryService;
import com.dzgylxt.service.IInquirySupplierService;
import com.dzgylxt.service.IQuotationService;
import com.dzgylxt.vo.purchase.InquiryComparisonVO;
import com.dzgylxt.vo.purchase.InquirySaveReqVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 询价管理（设计 §5.2：/api/v1/inquiries，含供应商范围与比价）。 */
@RestController
@RequestMapping("/api/v1/inquiries")
public class InquiryController extends BaseController<IInquiryService, Inquiry> {

    @Autowired
    private IInquiryService inquiryService;

    @Autowired
    private IInquirySupplierService inquirySupplierService;

    @Autowired
    private IQuotationService quotationService;

    @Override
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/page")
    public R<PageResult<Inquiry>> page(@RequestParam(defaultValue = "1") long current,
                                       @RequestParam(defaultValue = "10") long size) {
        Page<Inquiry> page = new Page<>(current, size);
        IPage<Inquiry> result = service.page(page,
                new LambdaQueryWrapper<Inquiry>().orderByDesc(Inquiry::getId));
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), current, size));
    }

    /** 创建询价（仅 APPROVED 申请）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping
    public R<Long> create(@RequestBody InquirySaveReqVO req) {
        return R.ok(inquiryService.createInquiry(req));
    }

    /** 发布（逐家准入校验 + 快照；≥1 家）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping("/{id}/publish")
    public R<Boolean> publish(@PathVariable Long id, @RequestBody(required = false) List<Long> supplierIds) {
        inquiryService.publish(id, supplierIds);
        return R.ok(true);
    }

    /** 截标。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping("/{id}/close")
    public R<Boolean> close(@PathVariable Long id) {
        inquiryService.close(id);
        return R.ok(true);
    }

    /** 取消。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping("/{id}/cancel")
    public R<Boolean> cancel(@PathVariable Long id) {
        inquiryService.cancel(id);
        return R.ok(true);
    }

    /** 供应商范围。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/{id}/suppliers")
    public R<List<InquirySupplier>> suppliers(@PathVariable Long id) {
        return R.ok(inquirySupplierService.list(
                new LambdaQueryWrapper<InquirySupplier>().eq(InquirySupplier::getInquiryId, id)));
    }

    /** 增补范围（逐家准入校验）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping("/{id}/suppliers")
    public R<Boolean> addSuppliers(@PathVariable Long id, @RequestBody List<Long> supplierIds) {
        inquirySupplierService.addSuppliers(id, supplierIds);
        return R.ok(true);
    }

    /** 移除范围（未发布可删）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @DeleteMapping("/{id}/suppliers/{supplierId}")
    public R<Boolean> removeSupplier(@PathVariable Long id, @PathVariable Long supplierId) {
        inquirySupplierService.removeSupplier(id, supplierId);
        return R.ok(true);
    }

    /** 报价包模板下载（设计 §5.2 /inquiries/{id}/quotation-template）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/{id}/quotation-template")
    public ResponseEntity<byte[]> quotationTemplate(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"quotation-template-" + id + ".xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(quotationService.exportTemplate(id));
    }

    /** 比价视图（按 SKU 最低/最高/均价 + 历史价）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/{id}/comparison")
    public R<InquiryComparisonVO> comparison(@PathVariable Long id) {
        return R.ok(inquiryService.comparison(id));
    }
}
