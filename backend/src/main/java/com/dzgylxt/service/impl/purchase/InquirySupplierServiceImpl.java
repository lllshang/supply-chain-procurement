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
 * <p>增补/刷新时逐家调用 {@link ISupplierService#getAdmission}，采用
 * <b>剔除式</b>口径（QA #25，主理人定稿）：{@code qualified=false}
 * （EXPIRED 资质 / 黑名单 / 停用）的供应商<b>剔除、不加入有效范围</b>
 * （{@code invited=0}，{@code admission_snapshot} 记录剔除原因留痕），
 * 合格者正常加入；剔除后有效范围为空时由发布流程返回 4000「无可用供应商」，
 * 不再整体 4000。快照仅为展示，定标/合同提交时仍实时重校（规格 §4.3 AC⑤）。</p>
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
            // 去重：已存在则刷新快照与剔除标记
            InquirySupplier existing = getOne(Wrappers.<InquirySupplier>lambdaQuery()
                    .eq(InquirySupplier::getInquiryId, inquiryId)
                    .eq(InquirySupplier::getSupplierId, supplierId)
                    .last("LIMIT 1"));
            InquirySupplier row = existing == null ? new InquirySupplier() : existing;
            row.setInquiryId(inquiryId);
            row.setSupplierId(supplierId);
            row.setQuoted(existing != null && existing.getQuoted() != null ? existing.getQuoted() : 0);
            // 剔除式（QA #25）：不合格 invited=0 留痕（快照含原因），合格 invited=1
            SupplierAdmissionVO admission = supplierService.getAdmission(supplierId);
            boolean qualified = admission != null && Boolean.TRUE.equals(admission.getQualified());
            row.setInvited(qualified ? 1 : 0);
            row.setAdmissionSnapshot(JSONUtil.toJsonStr(admission == null
                    ? new SnapshotStub(false, "供应商不存在")
                    : admission));
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

    /** 刷新某询价全部范围的准入快照与剔除标记（发布前统一调用；不合格剔除 invited=0 留痕）。 */
    @Override
    public void refreshSnapshots(Long inquiryId) {
        List<InquirySupplier> scope = list(Wrappers.<InquirySupplier>lambdaQuery()
                .eq(InquirySupplier::getInquiryId, inquiryId));
        for (InquirySupplier row : scope) {
            SupplierAdmissionVO admission = supplierService.getAdmission(row.getSupplierId());
            boolean qualified = admission != null && Boolean.TRUE.equals(admission.getQualified());
            row.setInvited(qualified ? 1 : 0);
            row.setAdmissionSnapshot(JSONUtil.toJsonStr(admission == null
                    ? new SnapshotStub(false, "供应商不存在")
                    : admission));
            updateById(row);
        }
    }

    /** 快照兜底桩（供应商不存在时无 VO 可序列化）。 */
    private record SnapshotStub(boolean qualified, String reasons) {
    }
}
