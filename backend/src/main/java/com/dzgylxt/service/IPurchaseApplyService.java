package com.dzgylxt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.approval.ApprovalCallbackHandler;
import com.dzgylxt.entity.purchase.PurchaseApply;
import com.dzgylxt.vo.purchase.ApplyDetailRespVO;
import com.dzgylxt.vo.purchase.ApplySaveReqVO;

/**
 * 采购申请服务（设计 §2.1）。
 */
public interface IPurchaseApplyService extends IService<PurchaseApply>, ApprovalCallbackHandler {

    /** 落头+明细：明细选有效 SKU、逐行落换算快照（selectCurrentEffective）、算金额。 */
    Long createApply(ApplySaveReqVO req);

    /** 仅 DRAFT/REJECTED 可编辑（状态机校验），整单替换明细。 */
    void updateApply(Long id, ApplySaveReqVO req);

    /** 提交：预算软校验（D3，仅提示）→ BUDGET_PENDING → PURCHASE_PENDING；发起 ApprovalGateway(PURCHASE_APPLY)。 */
    Long submit(Long id);

    /** 详情（头 + 明细含快照列）。 */
    ApplyDetailRespVO detail(Long id);

    /** 导出申请单（CSV，含明细与快照列；数据权限由拦截器过滤）。 */
    byte[] exportApply(Long id);

    /** 估算申请总额（Σ 明细 数量×预估价，缺预估价取 SKU 标准价）。 */
    java.math.BigDecimal estimateTotalAmount(Long applyId);
}
