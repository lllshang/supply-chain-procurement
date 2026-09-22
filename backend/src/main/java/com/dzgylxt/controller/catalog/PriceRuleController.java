package com.dzgylxt.controller.catalog;

import com.dzgylxt.common.R;
import com.dzgylxt.entity.catalog.PriceRule;
import com.dzgylxt.service.IPriceRuleService;
import com.dzgylxt.vo.catalog.PriceRuleSaveReqVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** 价格规则管理。 */
@RestController
@RequestMapping("/api/v1/catalog/price-rules")
public class PriceRuleController {

    private static final String GUARD = "isAuthenticated() and @authz.hasAnyPerm(authentication)";

    private final IPriceRuleService priceRuleService;

    public PriceRuleController(IPriceRuleService priceRuleService) {
        this.priceRuleService = priceRuleService;
    }

    @PreAuthorize(GUARD)
    @GetMapping("/list")
    public R<List<PriceRule>> list() {
        return R.ok(priceRuleService.list());
    }

    @PreAuthorize(GUARD)
    @GetMapping("/{id}")
    public R<PriceRule> getById(@PathVariable Long id) {
        return R.ok(priceRuleService.getById(id));
    }

    @PreAuthorize(GUARD)
    @PostMapping
    public R<Long> create(@RequestBody PriceRuleSaveReqVO req) {
        return R.ok(priceRuleService.createRule(req));
    }

    @PreAuthorize(GUARD)
    @PutMapping("/{id}")
    public R<Boolean> update(@PathVariable Long id, @RequestBody PriceRuleSaveReqVO req) {
        priceRuleService.updateRule(id, req);
        return R.ok(true);
    }

    @PreAuthorize(GUARD)
    @PostMapping("/{id}/invalidate")
    public R<Boolean> invalidate(@PathVariable Long id) {
        priceRuleService.invalidate(id);
        return R.ok(true);
    }

    @PreAuthorize(GUARD)
    @GetMapping("/validate")
    public R<Boolean> validatePrice(@RequestParam Integer refType,
                                    @RequestParam Long refId,
                                    @RequestParam BigDecimal price) {
        priceRuleService.validatePrice(refType, refId, price);
        return R.ok(true);
    }
}
