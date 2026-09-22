package com.dzgylxt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.catalog.SupplierQual;
import com.dzgylxt.enums.QualValidity;
import com.dzgylxt.vo.supplier.SupplierQualRespVO;
import com.dzgylxt.vo.supplier.SupplierQualSaveReqVO;

import java.util.List;

/**
 * 供应商资质服务（含审核闭环，A2/Q3 经 ApprovalGateway）。
 */
public interface ISupplierQualService extends IService<SupplierQual> {

    /** 后台录入资质（H5 提交属 P7，复用同模型）。 */
    Long createQual(SupplierQualSaveReqVO req);

    /** 编辑资质。 */
    void updateQual(Long id, SupplierQualSaveReqVO req);

    /** 提交审核：经 ApprovalGateway 发起（bizType=SUPPLIER_QUAL），返回审批任务 id。 */
    Long submitForApproval(Long qualId);

    /** 审批回调：通过→1；驳回→2+reject_reason。 */
    void onApprovalCallback(Long taskId, boolean approved, String comment);

    /** 驳回后修改重提 → 回到待审(0)。 */
    void resubmit(Long qualId, SupplierQualSaveReqVO req);

    /** 派生有效期：有效/即将到期/已过期。 */
    QualValidity deriveValidity(Long qualId);

    /** 某供应商全部资质（含派生有效期）。 */
    List<SupplierQualRespVO> listBySupplier(Long supplierId);

    /** 预警提前天数（配置项，默认 30）。 */
    int getWarnDays();
}
