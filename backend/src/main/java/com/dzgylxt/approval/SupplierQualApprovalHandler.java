package com.dzgylxt.approval;

import com.dzgylxt.entity.catalog.SupplierQual;
import com.dzgylxt.enums.QualStatus;
import com.dzgylxt.mapper.catalog.SupplierQualMapper;
import com.dzgylxt.security.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 供应商资质审批回调处理器（P4 设计 §2.8 缺口收敛：SUPPLIER_QUAL 逆向流 → 正向 handler）。
 *
 * <p><b>背景（偏差⑤ 收敛）</b>：现状 SupplierQualController → SupplierQualServiceImpl
 * .onApprovalCallback（更新资质状态）→ 反调 gateway.callback，为逆向回调流。
 * P4 引擎只认正向 handler 分发，本处理器把"通过=P3c 落 reviewed_by/status"逻辑平移至此；
 * 原 Controller 路径保留转发兼容（回归保障）。</p>
 *
 * <p><b>幂等</b>：资质已处于目标状态时跳过——正向路径与逆向转发路径重复触发不产生副作用。</p>
 */
@Slf4j
@Component
public class SupplierQualApprovalHandler implements ApprovalCallbackHandler {

    private final SupplierQualMapper supplierQualMapper;

    public SupplierQualApprovalHandler(SupplierQualMapper supplierQualMapper) {
        this.supplierQualMapper = supplierQualMapper;
    }

    @Override
    public String bizType() {
        return "SUPPLIER_QUAL";
    }

    @Override
    public void onApproved(Long taskId, Long bizId, String comment) {
        applyDecision(taskId, bizId, true, comment);
    }

    @Override
    public void onRejected(Long taskId, Long bizId, String comment) {
        applyDecision(taskId, bizId, false, comment);
    }

    /** 通过/驳回 → 资质 status 流转 + reviewed_by/at 留痕（逻辑平移自 SupplierQualServiceImpl）。 */
    private void applyDecision(Long taskId, Long bizId, boolean approved, String comment) {
        if (bizId == null) {
            log.warn("[P4-SupplierQualHandler] 任务缺 bizId，跳过资质流转 taskId={}", taskId);
            return;
        }
        SupplierQual qual = supplierQualMapper.selectById(bizId);
        if (qual == null) {
            log.warn("[P4-SupplierQualHandler] 资质不存在，跳过 taskId={} bizId={}", taskId, bizId);
            return;
        }
        QualStatus target = approved ? QualStatus.APPROVED : QualStatus.REJECTED;
        // 幂等：已处于目标状态时跳过（防逆向转发路径与正向路径双写）
        if (qual.getStatus() == target) {
            return;
        }
        qual.setStatus(target);
        qual.setRejectReason(approved ? null : comment);
        qual.setReviewedBy(UserContext.getCurrentUserId());
        qual.setReviewedAt(LocalDateTime.now());
        supplierQualMapper.updateById(qual);
    }
}
