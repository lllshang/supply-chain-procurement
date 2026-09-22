package com.dzgylxt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.purchase.InquirySupplier;

import java.util.List;

/** 询价供应商范围服务（设计 §2.2：增删时逐家准入校验 + 快照）。 */
public interface IInquirySupplierService extends IService<InquirySupplier> {

    /** 增补范围：逐家准入校验（不合格拒绝）+ 落 admission_snapshot；PUBLISHED 后仅保留快照追加口径。 */
    void addSuppliers(Long inquiryId, List<Long> supplierIds);

    /** 未发布可删。 */
    void removeSupplier(Long inquiryId, Long supplierId);

    /** 刷新某询价全部范围的准入快照（发布前统一调用）。 */
    void refreshSnapshots(Long inquiryId);
}
