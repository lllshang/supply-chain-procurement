package com.dzgylxt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.approval.ApprovalCallbackHandler;
import com.dzgylxt.entity.purchase.Award;
import com.dzgylxt.vo.purchase.AwardSaveReqVO;

import java.util.List;

/** 定标服务（设计 §2.4：定标只驱动合同，不生成订单）。 */
public interface IAwardService extends IService<Award>, ApprovalCallbackHandler {

    /** 生成 award_no + award_item 明细（按 SKU 可拆多供应商，落换算快照）。 */
    Long createAward(AwardSaveReqVO req);

    /** REJECTED 后调整重提 → PENDING_APPROVAL。 */
    void updateAwardItems(Long id, AwardSaveReqVO req);

    /** 发起 ApprovalGateway(AWARD)；异常价（低于历史均价阈值）标记人工复核提示。 */
    Long submit(Long id);

    /** 定标明细。 */
    List<com.dzgylxt.entity.purchase.AwardItem> listItems(Long awardId);
}
