package com.dzgylxt.service.impl.purchase;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.purchase.Inquiry;
import com.dzgylxt.entity.purchase.InquirySupplier;
import com.dzgylxt.enums.InquiryStatus;
import com.dzgylxt.mapper.purchase.InquiryMapper;
import com.dzgylxt.mapper.purchase.InquirySupplierMapper;
import com.dzgylxt.service.IInquirySupplierService;
import com.dzgylxt.service.ISupplierService;
import com.dzgylxt.vo.supplier.SupplierAdmissionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 询价供应商范围服务实现（设计 §2.2 + §7.1）。
 *
 * <p>增补/刷新时逐家调用 {@link ISupplierService#getAdmission}：
 * {@code qualified=false}（EXPIRED 资质 / 黑名单 / 停用）直接拒绝；
 * 合格则落 {@code admission_snapshot} JSON 审计快照（供回溯"当时为何放行"）。
 * 快照仅为展示，定标/合同提交时仍会实时重校（规格 §4.3 AC⑤）。</p>
 */
@Service
public class InquirySupplierServiceImpl extends ServiceImpl<InquirySupplierMapper, InquirySupplier>
        implements IInquirySupplierService {

    @Autowired
    private ISupplierService supplierService;

    @Autowired
    private InquiryMapper inquiryMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addSuppliers(Long inquiryId, List<Long> supplierIds) {
        Inquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "询价单不存在：" + inquiryId);
        }
        if (inquiry.getStatus() == InquiryStatus.CLOSED || inquiry.getStatus() == InquiryStatus.CANCELLED) {
            // CLOSED/CANCELLED 锁定范围
            throw new BizException(ResultCode.STATUS_INVALID, "询价已" + inquiry.getStatus().getDesc() + "，范围锁定");
        }
        if (supplierIds == null || supplierIds.isEmpty()) {
            return;
        }
        for (Long supplierId : supplierIds) {
            // 去重：已存在则刷新快照
            InquirySupplier existing = getOne(Wrappers.<InquirySupplier>lambdaQuery()
                    .eq(InquirySupplier::getInquiryId, inquiryId)
                    .eq(InquirySupplier::getSupplierId, supplierId)
                    .last("LIMIT 1"));
            InquirySupplier row = existing == null ? new InquirySupplier() : existing;
            row.setInquiryId(inquiryId);
            row.setSupplierId(supplierId);
            row.setInvited(1);
            row.setQuoted(existing != null && existing.getQuoted() != null ? existing.getQuoted() : 0);
            row.setAdmissionSnapshot(buildSnapshot(supplierId));
            if (existing == null) {
                save(row);
            } else {
                updateById(row);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeSupplier(Long inquiryId, Long supplierId) {
        Inquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry != null && inquiry.getStatus() != InquiryStatus.DRAFT) {
            throw new BizException(ResultCode.STATUS_INVALID, "仅未发布询价可移除供应商");
        }
        remove(Wrappers.<InquirySupplier>lambdaQuery()
                .eq(InquirySupplier::getInquiryId, inquiryId)
                .eq(InquirySupplier::getSupplierId, supplierId));
    }

    /** 刷新某询价全部范围的准入快照（发布前统一调用）。 */
    @Override
    public void refreshSnapshots(Long inquiryId) {
        List<InquirySupplier> scope = list(Wrappers.<InquirySupplier>lambdaQuery()
                .eq(InquirySupplier::getInquiryId, inquiryId));
        for (InquirySupplier row : scope) {
            row.setAdmissionSnapshot(buildSnapshot(row.getSupplierId()));
            updateById(row);
        }
    }

    /** 实时校验 + 快照 JSON：不合格直接拒绝（准入拦截，发布口径）。 */
    private String buildSnapshot(Long supplierId) {
        SupplierAdmissionVO admission = supplierService.getAdmission(supplierId);
        if (admission == null || !Boolean.TRUE.equals(admission.getQualified())) {
            String reason = admission == null ? "供应商不存在" : String.join("；", admission.getReasons());
            throw new BizException(ResultCode.PARAM_ERROR, "供应商准入校验未通过：" + supplierId + "（" + reason + "）");
        }
        return cn.hutool.json.JSONUtil.toJsonStr(admission);
    }
}
