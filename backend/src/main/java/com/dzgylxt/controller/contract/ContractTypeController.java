package com.dzgylxt.controller.contract;

import com.dzgylxt.common.R;
import com.dzgylxt.entity.contract.ContractType;
import com.dzgylxt.service.IContractTypeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 合同类型字典 CRUD（P4 R3a，设计 §6.4：新键 contract:type:read / contract:type:write）。
 */
@RestController
@RequestMapping("/api/v1/contract-types")
public class ContractTypeController {

    private static final String PERM_READ =
            "isAuthenticated() and @authz.hasPerm(authentication,'contract:type:read')";
    private static final String PERM_WRITE =
            "isAuthenticated() and @authz.hasPerm(authentication,'contract:type:write')";

    private final IContractTypeService contractTypeService;

    public ContractTypeController(IContractTypeService contractTypeService) {
        this.contractTypeService = contractTypeService;
    }

    /** 列表（含停用，配置页管理用）。 */
    @PreAuthorize(PERM_READ)
    @GetMapping
    public R<List<ContractType>> list() {
        return R.ok(contractTypeService.list());
    }

    @PreAuthorize(PERM_WRITE)
    @PostMapping
    public R<Long> create(@RequestBody ContractType type) {
        return R.ok(contractTypeService.createType(type));
    }

    @PreAuthorize(PERM_WRITE)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody ContractType patch) {
        contractTypeService.updateType(id, patch);
        return R.ok(true);
    }

    /** 删除（被引用拒删，PRD L840；一般用停用代替）。 */
    @PreAuthorize(PERM_WRITE)
    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable Long id) {
        contractTypeService.deleteType(id);
        return R.ok(true);
    }
}
