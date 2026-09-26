package com.dzgylxt.controller.contract;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dzgylxt.common.PageResult;
import com.dzgylxt.common.R;
import com.dzgylxt.entity.contract.Contract;
import com.dzgylxt.service.IContractService;
import com.dzgylxt.vo.contract.ContractRenewReqVO;
import com.dzgylxt.vo.contract.ContractSaveReqVO;
import com.dzgylxt.vo.contract.ContractWarnVO;
import org.springframework.beans.factory.annotation.Autowired;
import com.dzgylxt.common.SecurityConstants;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 合同管理（设计 §5.4：/api/v1/contracts；独立控制器）。 */
@RestController
@RequestMapping("/api/v1/contracts")
public class ContractController {

    @Autowired
    private IContractService contractService;

    /** 分页（id 倒序）。 */
    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/page")
    public R<PageResult<Contract>> page(@RequestParam(defaultValue = "1") long current,
                                        @RequestParam(defaultValue = "10") long size) {
        Page<Contract> page = new Page<>(current, size);
        IPage<Contract> result = contractService.page(page,
                new LambdaQueryWrapper<Contract>().orderByDesc(Contract::getId));
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), current, size));
    }

    /** 单据详情。 */
    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/{id}")
    public R<Contract> getById(@PathVariable Long id) {
        return R.ok(contractService.getById(id));
    }

    /** 合同登记（准入校验 + 定标金额核对）。 */
    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping
    public R<Long> create(@RequestBody ContractSaveReqVO req) {
        return R.ok(contractService.createContract(req));
    }

    /** 编辑（仅 DRAFT）。 */
    @PreAuthorize(SecurityConstants.GUARD)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody ContractSaveReqVO req) {
        contractService.updateContract(id, req);
        return R.ok(true);
    }

    /** 提交审批（金额超阈值升两级）。 */
    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping("/{id}/submit")
    public R<Long> submit(@PathVariable Long id) {
        return R.ok(contractService.submit(id));
    }

    /** 终止（仅 EFFECTIVE；额度冻结）。 */
    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping("/{id}/terminate")
    public R<Boolean> terminate(@PathVariable Long id, @RequestBody TerminateReq req) {
        contractService.terminate(id, req.getReason());
        return R.ok(true);
    }

    /** 续签（新合同独立走审批）。 */
    @PreAuthorize(SecurityConstants.GUARD)
    @PostMapping("/{id}/renew")
    public R<Long> renew(@PathVariable Long id, @RequestBody ContractRenewReqVO req) {
        return R.ok(contractService.renew(id, req));
    }

    /** 补充签订（P4 R3a：新合同 source_contract_id=原合同、relation_type=SUPPLEMENT，走 CONTRACT 审批）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasPerm(authentication,'contract:write')")
    @PostMapping("/{id}/supplement")
    public R<Long> supplement(@PathVariable Long id,
                              @RequestBody com.dzgylxt.vo.contract.ContractSupplementReqVO req) {
        return R.ok(contractService.supplement(id, req));
    }

    /** SKU 白名单（P4 D15：维护，设计 §6.4）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasPerm(authentication,'contract:write')")
    @org.springframework.web.bind.annotation.PutMapping("/{id}/sku-whitelist")
    public R<Boolean> replaceSkuWhitelist(@PathVariable Long id,
                                          @RequestBody java.util.List<com.dzgylxt.vo.contract.SkuWhitelistReqVO> items) {
        return R.ok(contractService.replaceSkuWhitelist(id, items));
    }

    /** SKU 白名单查看（P4 D15）。 */
    @PreAuthorize("isAuthenticated() and @authz.hasPerm(authentication,'contract:read')")
    @org.springframework.web.bind.annotation.GetMapping("/{id}/sku-whitelist")
    public R<java.util.List<com.dzgylxt.entity.contract.ContractSkuWhitelist>> skuWhitelist(@PathVariable Long id) {
        return R.ok(contractService.listSkuWhitelist(id));
    }

    /** 到期预警（valid_to − 提前天数配置）。 */
    @PreAuthorize(SecurityConstants.GUARD)
    @GetMapping("/expiring-warn")
    public R<List<ContractWarnVO>> expiringWarn() {
        return R.ok(contractService.expiringList());
    }

    /** 终止请求体。 */
    public static class TerminateReq {
        private String reason;

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
