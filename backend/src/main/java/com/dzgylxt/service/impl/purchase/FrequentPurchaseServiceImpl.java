package com.dzgylxt.service.impl.purchase;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.common.BizException;
import com.dzgylxt.enums.ProductStatus;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.catalog.Sku;
import com.dzgylxt.entity.purchase.FrequentPurchase;
import com.dzgylxt.enums.FrequentStatus;
import com.dzgylxt.enums.ItemType;
import com.dzgylxt.mapper.catalog.SkuMapper;
import com.dzgylxt.mapper.purchase.FrequentPurchaseMapper;
import com.dzgylxt.service.IFrequentPurchaseService;
import com.dzgylxt.vo.purchase.ApplyItemDraftVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 部门常购清单服务实现（设计 §2.1）。 */
@Service
public class FrequentPurchaseServiceImpl extends ServiceImpl<FrequentPurchaseMapper, FrequentPurchase>
        implements IFrequentPurchaseService {

    @Autowired
    private SkuMapper skuMapper;

    @Override
    public List<FrequentPurchase> listByDept(Long deptId) {
        return list(Wrappers.<FrequentPurchase>lambdaQuery()
                .eq(FrequentPurchase::getDeptId, deptId)
                .orderByDesc(FrequentPurchase::getId));
    }

    @Override
    public List<ApplyItemDraftVO> bringIn(Long deptId, List<Long> skuIds) {
        List<ApplyItemDraftVO> drafts = new ArrayList<>();
        if (skuIds == null || skuIds.isEmpty()) {
            return drafts;
        }
        for (Long skuId : skuIds) {
            Sku sku = skuMapper.selectById(skuId);
            if (sku == null || sku.getStatus() == null || sku.getStatus() != ProductStatus.NORMAL) {
                // 停用 SKU 拒绝带入（设计 §2.1）
                throw new BizException(ResultCode.PARAM_ERROR, "SKU 无效或已停用，不可带入：" + skuId);
            }
            ApplyItemDraftVO draft = new ApplyItemDraftVO();
            draft.setSkuId(skuId);
            draft.setSkuCode(sku.getSkuCode());

            // 常购配置（可选）：默认数量与采购单位
            FrequentPurchase frequent = getOne(Wrappers.<FrequentPurchase>lambdaQuery()
                    .eq(FrequentPurchase::getDeptId, deptId)
                    .eq(FrequentPurchase::getSkuId, skuId)
                    .last("LIMIT 1"));
            draft.setFrequentId(frequent == null ? null : frequent.getId());
            draft.setQty(frequent == null || frequent.getDefaultQty() == null
                    ? BigDecimal.ONE : frequent.getDefaultQty());
            draft.setPurchaseUnit(frequent != null && frequent.getPurchaseUnit() != null
                    ? frequent.getPurchaseUnit()
                    : (sku.getPurchaseUnit() != null ? sku.getPurchaseUnit() : sku.getBaseUnit()));

            // 最近价：常购 last_price（最近订单/报价价维护口）→ 无则 SKU 标准价
            BigDecimal price = frequent == null ? null : frequent.getLastPrice();
            if (price == null) {
                // P3c-A6：与申请/合同取价同口径（标准价优先 → fallback 参考价）
                price = PurchaseApplyServiceImpl.resolveSkuPrice(sku);
            }
            draft.setPriceEstimate(price);
            draft.setItemType(ItemType.MATERIAL);
            drafts.add(draft);
        }
        return drafts;
    }
}
