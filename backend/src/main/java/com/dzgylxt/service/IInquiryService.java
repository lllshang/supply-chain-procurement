package com.dzgylxt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.purchase.Inquiry;
import com.dzgylxt.vo.purchase.InquiryComparisonVO;
import com.dzgylxt.vo.purchase.InquirySaveReqVO;

import java.util.List;

/** 询价单服务（设计 §2.2）。 */
public interface IInquiryService extends IService<Inquiry> {

    /** 创建询价：仅 APPROVED 申请（或明细子集，SKU 集合取申请明细）；生成 inquiry_no。 */
    Long createInquiry(InquirySaveReqVO req);

    /** 发布：≥1 家供应商；逐家 getAdmission，不合格拒绝并落 admission_snapshot 快照；状态→PUBLISHED。 */
    void publish(Long id, List<Long> supplierIds);

    /** 截标（到 deadline 定时或手动）→ CLOSED，锁定范围。 */
    void close(Long id);

    /** DRAFT/PUBLISHED → CANCELLED。 */
    void cancel(Long id);

    /** 比价视图：按 SKU 汇总最低/最高/均价 + 历史价（近 5 次）。 */
    InquiryComparisonVO comparison(Long id);
}
