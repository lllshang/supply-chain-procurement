package com.dzgylxt.service.impl.order;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.approval.ApprovalGateway;
import com.dzgylxt.approval.ApprovalTaskSpec;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.order.FulfillmentAdjust;
import com.dzgylxt.entity.order.PurchaseOrder;
import com.dzgylxt.enums.AdjustStatus;
import com.dzgylxt.enums.AdjustType;
import com.dzgylxt.enums.OrderStatus;
import com.dzgylxt.mapper.order.FulfillmentAdjustMapper;
import com.dzgylxt.mapper.order.PurchaseOrderMapper;
import com.dzgylxt.security.UserContext;
import com.dzgylxt.service.IFulfillmentAdjustService;
import com.dzgylxt.vo.order.AdjustSaveReqVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 履约调整服务实现（设计 §2.8；R3 修订）。
 *
 * <p>R3（审计 §6.7.3 L776/L774）：</p>
 * <ul>
 *   <li><b>取消 5% 免审阈值</b>——调整一律进审批单据中心（bizType=FULFILLMENT_ADJUST）；
 *       状态机：DRAFT →(提交) IN_APPROVAL → 生效(2) / 驳回(3)→DRAFT；</li>
 *   <li><b>放开"部分到货可调整"</b>——仅"全部完成（RECEIVED）/已结算（SETTLED/PAID）/
 *       已取消（CANCELLED）"不可调整，PARTIAL_RECEIVED 仍可调整；</li>
 *   <li>超付预付款退款/转余额/抵扣本轮不做（已登记 D10/P3b）。</li>
 * </ul>
 */
@Service
public class FulfillmentAdjustServiceImpl extends ServiceImpl<FulfillmentAdjustMapper, FulfillmentAdjust>
        implements IFulfillmentAdjustService {

    /** 审批 bizType（设计 §3）。 */
    public static final String BIZ_TYPE = "FULFILLMENT_ADJUST";

    @Autowired
    private PurchaseOrderMapper orderMapper;

    @Autowired
    private ApprovalGateway approvalGateway;

    @Autowired
    private BusinessNoGenerator businessNoGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAdjust(AdjustSaveReqVO req) {
        if (req.getOrderId() == null || req.getAdjustType() == null
                || req.getReason() == null || req.getReason().isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "订单/调整类型/原因均必填");
        }
        PurchaseOrder order = orderMapper.selectById(req.getOrderId());
        if (order == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "订单不存在：" + req.getOrderId());
        }
        checkAdjustable(order);
        FulfillmentAdjust adjust = new FulfillmentAdjust();
        adjust.setAdjustNo(businessNoGenerator.nextNo("LY"));
        adjust.setBizType(req.getBizType());
        adjust.setOrderId(req.getOrderId());
        adjust.setArrivalId(req.getArrivalId());
        adjust.setAdjustType(req.getAdjustType());
        adjust.setBeforeJson(req.getBeforeJson());
        // 涉及金额落入 after 快照（表无金额列，快照即口径；阈值判定读取）
        JSONObject afterJson = req.getAfterJson() == null || req.getAfterJson().isBlank()
                ? new JSONObject() : JSONUtil.parseObj(req.getAfterJson());
        if (req.getAmount() != null) {
            afterJson.set("amount", req.getAmount());
        }
        adjust.setAfterJson(afterJson.toString());
        adjust.setReason(req.getReason());
        adjust.setStatus(AdjustStatus.DRAFT);
        adjust.setFileKeys(req.getFileKeys() == null ? null : JSONUtil.toJsonStr(req.getFileKeys()));
        adjust.setOperator(UserContext.getCurrentUserId());
        save(adjust);
        return adjust.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(Long id) {
        FulfillmentAdjust adjust = getById(id);
        if (adjust == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "调整单不存在：" + id);
        }
        if (adjust.getStatus() != AdjustStatus.DRAFT) {
            throw new BizException(ResultCode.STATUS_INVALID, "仅草稿调整单可提交");
        }
        PurchaseOrder order = orderMapper.selectById(adjust.getOrderId());
        if (order == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "订单不存在：" + adjust.getOrderId());
        }
        checkAdjustable(order);

        // R3：取消 5% 免审——调整一律进审批单据中心（bizType=FULFILLMENT_ADJUST）
        BigDecimal adjustAmount = adjustAmountOf(adjust);
        adjust.setStatus(AdjustStatus.IN_APPROVAL);
        ApprovalTaskSpec spec = new ApprovalTaskSpec();
        spec.setBizType(BIZ_TYPE);
        spec.setBizId(adjust.getId());
        spec.setTitle("履约调整-" + adjust.getAdjustNo());
        spec.setApplicant(UserContext.getCurrentUsername());
        spec.setPayloadJson(JSONUtil.toJsonStr(new Object() {
            public final int adjustType = adjust.getAdjustType() == null ? 0 : adjust.getAdjustType().getValue();
            public final BigDecimal involvedAmount = adjustAmount;
            public final BigDecimal beforeAmount = order.getBudgetOccupied();
            public final BigDecimal afterAmount = order.getBudgetOccupied() == null
                    ? adjustAmount : order.getBudgetOccupied().add(adjustAmount);
        }));
        approvalGateway.create(spec);
        updateById(adjust);
        return adjust.getId();
    }

    @Override
    public IPage<FulfillmentAdjust> pageAdjust(long current, long size, Long orderId, AdjustType adjustType) {
        LambdaQueryWrapper<FulfillmentAdjust> wrapper = new LambdaQueryWrapper<>();
        if (orderId != null) {
            wrapper.eq(FulfillmentAdjust::getOrderId, orderId);
        }
        if (adjustType != null) {
            wrapper.eq(FulfillmentAdjust::getAdjustType, adjustType);
        }
        return page(new Page<>(current, size), wrapper.orderByDesc(FulfillmentAdjust::getId));
    }

    /** 调整涉及金额（after 快照 amount 字段，缺省 0；仅作审批 payload 展示口径）。 */
    private BigDecimal adjustAmountOf(FulfillmentAdjust adjust) {
        if (adjust.getAfterJson() == null || adjust.getAfterJson().isBlank()) {
            return BigDecimal.ZERO;
        }
        JSONObject after = JSONUtil.parseObj(adjust.getAfterJson());
        BigDecimal amount = after.getBigDecimal("amount");
        return amount == null ? BigDecimal.ZERO : amount;
    }

    /**
     * 可调整性校验（R3，PRD L774/L777）：仅"全部完成/已结算部分"不可调整——
     * REJECTED/RECEIVED（全部完成）、SETTLED/PAID（已结算）不可调整；
     * CREATED/PARTIAL_RECEIVED（含部分到货）可调整。
     */
    private void checkAdjustable(PurchaseOrder order) {
        OrderStatus status = order.getStatus();
        if (status == OrderStatus.RECEIVED || status == OrderStatus.SETTLED
                || status == OrderStatus.PAID || status == OrderStatus.CANCELLED) {
            throw new BizException(ResultCode.STATUS_INVALID,
                    "订单已全部完成/已结算/已取消，不可调整：" + (status == null ? "-" : status.getDesc()));
        }
    }

    // ---------------- ApprovalCallbackHandler（bizType=FULFILLMENT_ADJUST） ----------------

    @Override
    public String bizType() {
        return BIZ_TYPE;
    }

    @Override
    public void onApproved(Long taskId, Long bizId, String comment) {
        FulfillmentAdjust adjust = getById(bizId);
        if (adjust == null || adjust.getStatus() != AdjustStatus.IN_APPROVAL) {
            return;
        }
        // 生效 → 回写相关单据（差异完成/退货跟踪闭环，台账口径承接）
        adjust.setStatus(AdjustStatus.EFFECTIVE);
        updateById(adjust);
    }

    @Override
    public void onRejected(Long taskId, Long bizId, String comment) {
        FulfillmentAdjust adjust = getById(bizId);
        if (adjust == null || adjust.getStatus() != AdjustStatus.IN_APPROVAL) {
            return;
        }
        adjust.setStatus(AdjustStatus.DRAFT);
        updateById(adjust);
    }
}
