package com.dzgylxt.controller.settlement;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dzgylxt.common.PageResult;
import com.dzgylxt.common.R;
import com.dzgylxt.entity.settlement.Payment;
import com.dzgylxt.service.IPaymentService;
import com.dzgylxt.vo.settlement.PaymentSaveReqVO;
import com.dzgylxt.vo.settlement.StatementVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 付款登记管理（P3 设计 §5：/api/v1/payments；独立控制器不走通用 save）。
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    @Autowired
    private IPaymentService paymentService;

    /** 分页（结算单过滤）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/page")
    public R<PageResult<Payment>> page(@RequestParam(defaultValue = "1") long current,
                                       @RequestParam(defaultValue = "10") long size,
                                       @RequestParam(required = false) Long settlementId) {
        LambdaQueryWrapper<Payment> wrapper = new LambdaQueryWrapper<>();
        if (settlementId != null) {
            wrapper.eq(Payment::getSettlementId, settlementId);
        }
        Page<Payment> page = new Page<>(current, size);
        IPage<Payment> result = paymentService.page(page, wrapper.orderByDesc(Payment::getId));
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), current, size));
    }

    /** 单据。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/{id}")
    public R<Payment> getById(@PathVariable Long id) {
        return R.ok(paymentService.getById(id));
    }

    /** 创建付款单。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping
    public R<Long> create(@RequestBody PaymentSaveReqVO req) {
        return R.ok(paymentService.createPayment(req));
    }

    /** 修改重提。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody PaymentSaveReqVO req) {
        paymentService.updatePayment(id, req);
        return R.ok(true);
    }

    /** 线下付款登记确认（R6：免审批，财务直接登记；凭证+日期）→ PAID。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @PostMapping("/{id}/confirm")
    public R<Boolean> confirm(@PathVariable Long id, @RequestBody ConfirmReq req) {
        paymentService.confirmPayment(id, req.getVoucherFile(), req.getPayDate());
        return R.ok(true);
    }

    /** 供应商对账单（应付/已付/差额 + 明细）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/statement")
    public R<StatementVO> statement(@RequestParam Long supplierId,
                                    @RequestParam(required = false)
                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false)
                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(paymentService.statement(supplierId, from, to));
    }

    /** 供应商对账单 Excel 导出（T06）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasAnyPerm(authentication)")
    @GetMapping("/statement/export")
    public ResponseEntity<byte[]> statementExport(@RequestParam Long supplierId,
                                                  @RequestParam(required = false)
                                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false)
                                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        StatementVO vo = paymentService.statement(supplierId, from, to);
        List<List<String>> head = List.of(
                List.of("日期", "单号", "方向", "金额", "备注"));
        List<List<Object>> rows = vo.getRows().stream()
                .<List<Object>>map(r -> List.of(r.getDate() == null ? "" : r.getDate().toString(),
                        r.getDocNo(), r.getDirection(), (Object) r.getAmount(),
                        r.getRemark() == null ? "" : r.getRemark()))
                .toList();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        com.alibaba.excel.EasyExcel.write(out)
                .head(head)
                .sheet("对账单")
                .doWrite(rows);
        byte[] bytes = out.toByteArray();
        String filename = "statement-" + supplierId + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    /** 登记确认请求体。 */
    public static class ConfirmReq {
        private String voucherFile;
        private LocalDate payDate;

        public String getVoucherFile() {
            return voucherFile;
        }

        public void setVoucherFile(String voucherFile) {
            this.voucherFile = voucherFile;
        }

        public LocalDate getPayDate() {
            return payDate;
        }

        public void setPayDate(LocalDate payDate) {
            this.payDate = payDate;
        }
    }
}
