package com.dzgylxt.service.impl.catalog;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.approval.ApprovalDecision;
import com.dzgylxt.approval.ApprovalGateway;
import com.dzgylxt.approval.ApprovalTaskSpec;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.QualValidityCalculator;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.approval.ApprovalTask;
import com.dzgylxt.entity.catalog.SupplierQual;
import com.dzgylxt.enums.QualStatus;
import com.dzgylxt.enums.QualValidity;
import com.dzgylxt.mapper.approval.ApprovalTaskMapper;
import com.dzgylxt.mapper.catalog.SupplierMapper;
import com.dzgylxt.mapper.catalog.SupplierQualMapper;
import com.dzgylxt.security.UserContext;
import com.dzgylxt.service.ISupplierQualService;
import com.dzgylxt.vo.supplier.SupplierQualRespVO;
import com.dzgylxt.vo.supplier.SupplierQualSaveReqVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 供应商资质服务实现（含审核闭环，A2/Q3 经 {@link ApprovalGateway}）。
 */
@Service
public class SupplierQualServiceImpl extends ServiceImpl<SupplierQualMapper, SupplierQual>
        implements ISupplierQualService {

    /** 资质审核业务类型。 */
    private static final String BIZ_TYPE = "SUPPLIER_QUAL";

    private final ApprovalGateway approvalGateway;
    private final ApprovalTaskMapper approvalTaskMapper;
    private final SupplierMapper supplierMapper;

    @Value("${app.supplier.qual-warn-days:30}")
    private int qualWarnDays;

    public SupplierQualServiceImpl(ApprovalGateway approvalGateway,
                                   ApprovalTaskMapper approvalTaskMapper,
                                   SupplierMapper supplierMapper) {
        this.approvalGateway = approvalGateway;
        this.approvalTaskMapper = approvalTaskMapper;
        this.supplierMapper = supplierMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createQual(SupplierQualSaveReqVO req) {
        if (req.getSupplierId() == null || supplierMapper.selectById(req.getSupplierId()) == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "供应商不存在：" + req.getSupplierId());
        }
        if (!StringUtils.hasText(req.getType())) {
            throw new BizException(ResultCode.PARAM_ERROR, "资质类型必填");
        }
        SupplierQual entity = new SupplierQual();
        apply(req, entity);
        entity.setSupplierId(req.getSupplierId());
        entity.setStatus(QualStatus.PENDING);
        save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQual(Long id, SupplierQualSaveReqVO req) {
        SupplierQual entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "资质不存在：" + id);
        }
        if (entity.getStatus() == QualStatus.APPROVED) {
            throw new BizException(ResultCode.STATUS_INVALID, "资质已通过，请先驳回或作废后再修改");
        }
        apply(req, entity);
        updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitForApproval(Long qualId) {
        SupplierQual qual = getById(qualId);
        if (qual == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "资质不存在：" + qualId);
        }
        if (qual.getStatus() == QualStatus.APPROVED) {
            throw new BizException(ResultCode.STATUS_INVALID, "资质已通过，无需重复提交审核");
        }
        ApprovalTaskSpec spec = new ApprovalTaskSpec();
        spec.setBizType(BIZ_TYPE);
        spec.setBizId(qualId);
        spec.setTitle("供应商资质审核：" + (qual.getQualName() == null ? qual.getType() : qual.getQualName()));
        spec.setApplicant(UserContext.getCurrentUsername());
        spec.setPayloadJson("{\"qualId\":" + qualId + ",\"supplierId\":" + qual.getSupplierId() + "}");
        return approvalGateway.create(spec);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onApprovalCallback(Long taskId, boolean approved, String comment) {
        ApprovalTask task = approvalTaskMapper.selectById(taskId);
        if (task == null || task.getBizId() == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "审批任务不存在或缺少业务单号：" + taskId);
        }
        SupplierQual qual = getById(task.getBizId());
        if (qual == null) {
            return;
        }
        if (!approved && !StringUtils.hasText(comment)) {
            throw new BizException(ResultCode.PARAM_ERROR, "驳回必须填写原因");
        }
        qual.setStatus(approved ? QualStatus.APPROVED : QualStatus.REJECTED);
        qual.setRejectReason(approved ? null : comment);
        qual.setReviewedBy(UserContext.getCurrentUserId());
        qual.setReviewedAt(LocalDateTime.now());
        updateById(qual);
        approvalGateway.callback(taskId, approved ? ApprovalDecision.APPROVED : ApprovalDecision.REJECTED, comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resubmit(Long qualId, SupplierQualSaveReqVO req) {
        SupplierQual qual = getById(qualId);
        if (qual == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "资质不存在：" + qualId);
        }
        if (qual.getStatus() != QualStatus.REJECTED) {
            throw new BizException(ResultCode.STATUS_INVALID, "仅驳回状态可修改重提");
        }
        apply(req, qual);
        qual.setStatus(QualStatus.PENDING);
        qual.setRejectReason(null);
        qual.setReviewedBy(null);
        qual.setReviewedAt(null);
        updateById(qual);
    }

    @Override
    public QualValidity deriveValidity(Long qualId) {
        SupplierQual qual = getById(qualId);
        if (qual == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "资质不存在：" + qualId);
        }
        return QualValidityCalculator.compute(qual.getExpireAt(), qualWarnDays);
    }

    @Override
    public List<SupplierQualRespVO> listBySupplier(Long supplierId) {
        List<SupplierQual> quals = baseMapper.selectBySupplier(supplierId);
        List<SupplierQualRespVO> result = new ArrayList<>();
        for (SupplierQual qual : quals) {
            SupplierQualRespVO vo = new SupplierQualRespVO();
            vo.setId(qual.getId());
            vo.setSupplierId(qual.getSupplierId());
            vo.setType(qual.getType());
            vo.setQualName(qual.getQualName());
            vo.setFileKey(qual.getFileKey());
            vo.setExpireAt(qual.getExpireAt());
            vo.setStatus(qual.getStatus() == null ? null : qual.getStatus().getValue());
            vo.setValidity(QualValidityCalculator.compute(qual.getExpireAt(), qualWarnDays).name());
            vo.setRejectReason(qual.getRejectReason());
            vo.setReviewedBy(qual.getReviewedBy());
            vo.setReviewedAt(qual.getReviewedAt());
            vo.setUpdatedAt(qual.getUpdatedAt());
            result.add(vo);
        }
        return result;
    }

    @Override
    public int getWarnDays() {
        return qualWarnDays;
    }

    private void apply(SupplierQualSaveReqVO req, SupplierQual entity) {
        if (StringUtils.hasText(req.getType())) {
            entity.setType(req.getType());
        }
        entity.setQualName(req.getQualName());
        entity.setFileKey(req.getFileKey());
        entity.setExpireAt(req.getExpireAt());
    }
}
