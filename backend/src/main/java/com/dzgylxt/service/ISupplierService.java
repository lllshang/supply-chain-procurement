package com.dzgylxt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.catalog.Supplier;
import com.dzgylxt.enums.CoopStatus;
import com.dzgylxt.vo.supplier.SupplierAdmissionVO;
import com.dzgylxt.vo.supplier.SupplierPageReqVO;
import com.dzgylxt.vo.supplier.SupplierPageRespVO;
import com.dzgylxt.vo.supplier.SupplierSaveReqVO;

/**
 * 供应商服务。
 */
public interface ISupplierService extends IService<Supplier> {

    /** 新增供应商：creditCode 唯一 + 分类存在 + 默认 coop_status=0/is_blacklist=0。 */
    Long createSupplier(SupplierSaveReqVO req);

    /** 编辑供应商。 */
    void updateSupplier(Long id, SupplierSaveReqVO req);

    /** 组合检索。 */
    IPage<SupplierPageRespVO> pageSupplier(SupplierPageReqVO req);

    /** 更新合作状态。 */
    void updateCoopStatus(Long id, CoopStatus status);

    /** 标记黑名单。 */
    void markBlacklist(Long id, boolean blacklist);

    /** 准入资格派生（R-X-01，只读）。 */
    SupplierAdmissionVO getAdmission(Long supplierId);
}
