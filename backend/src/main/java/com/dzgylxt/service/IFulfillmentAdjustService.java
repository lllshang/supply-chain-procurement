package com.dzgylxt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.approval.ApprovalCallbackHandler;
import com.dzgylxt.entity.order.FulfillmentAdjust;
import com.dzgylxt.vo.order.AdjustSaveReqVO;

/** 履约调整服务（设计 §2.8：阈值内免审直接生效；超阈值走 FULFILLMENT_ADJUST 审批 <!-- D5 -->）。 */
public interface IFulfillmentAdjustService extends IService<FulfillmentAdjust>, ApprovalCallbackHandler {

    /** 生成 adjust_no；before/after 快照；关联 order/arrival；状态 DRAFT。 */
    Long createAdjust(AdjustSaveReqVO req);

    /** 提交：金额 ≤ 合同金额×阈值（默认 5%，app.adjust.approve-threshold）→直接生效；超阈值→审批中。 */
    Long submit(Long id);

    /** 台账筛选（类型/订单/供应商/时间）+ 导出复用分页结果。 */
    IPage<FulfillmentAdjust> pageAdjust(long current, long size, Long orderId,
                                        com.dzgylxt.enums.AdjustType adjustType);
}
