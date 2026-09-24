package com.dzgylxt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.approval.ApprovalCallbackHandler;
import com.dzgylxt.entity.purchase.Award;
import com.dzgylxt.vo.purchase.AwardSaveReqVO;

import java.util.List;

/** 定标服务（设计 §2.4：定标只驱动合同，不生成订单；R2：一询价单一中标供应商）。 */
public interface IAwardService extends IService<Award>, ApprovalCallbackHandler {

    /** 生成 award_no + award_item 明细（R2：仅同供应商明细行，落换算快照）。 */
    Long createAward(AwardSaveReqVO req);

    /** REJECTED 后调整重提 → PENDING_APPROVAL。 */
    void updateAwardItems(Long id, AwardSaveReqVO req);

    /**
     * 发起 ApprovalGateway(AWARD)；异常价（低于历史均价阈值）标记人工复核提示；
     * R7：提交时预算再校验（部门×科目×月，仅校验不占用），不足→拦截并转 BUDGET 升级审批。
     */
    Long submit(Long id);

    /** R7：BUDGET 升级审批通过后的放行确认——补发 AWARD 审批（预算仅校验不占用，无回滚动作）。 */
    void releaseAfterBudgetApproval(Long awardId);

    /**
     * 作废/关闭（P2b-3）：驳回未用、审批前放弃的定标终态闭环——
     * 释放该定标全部 AWARD 占用（RELEASE 负向流水 + used 回退）+ 状态流转 VOIDED + 留痕。
     * 已登记合同的定标不可作废（合同链持有锚点，需先终止合同）。
     */
    void voidAward(Long id, String reason);

    /** 定标明细。 */
    List<com.dzgylxt.entity.purchase.AwardItem> listItems(Long awardId);
}
