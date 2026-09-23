package com.dzgylxt.service.impl.settlement;

import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.approval.ApprovalGateway;
import com.dzgylxt.approval.ApprovalTaskSpec;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.order.PurchaseOrder;
import com.dzgylxt.entity.settlement.Payment;
import com.dzgylxt.entity.settlement.Settlement;
import com.dzgylxt.enums.OrderStatus;
import com.dzgylxt.enums.PaymentStatus;
import com.dzgylxt.enums.SettlementStatus;
import com.dzgylxt.mapper.order.PurchaseOrderMapper;
import com.dzgylxt.mapper.order.OrderItemMapper;
import com.dzgylxt.entity.order.OrderItem;
import com.dzgylxt.mapper.settlement.PaymentMapper;
import com.dzgylxt.mapper.settlement.SettlementMapper;
import com.dzgylxt.security.UserContext;
import com.dzgylxt.service.IPaymentService;
import com.dzgylxt.vo.settlement.PaymentSaveReqVO;
import com.dzgylxt.vo.settlement.StatementVO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 付款登记服务实现（P3 设计 §2.2 / T05+T06）。
 *
 * <p>两阶段付款：PAYMENT 审批通过（财务审，reviewed_by/at 落）→ 线下付款后
 * {@link #confirmPayment} 登记凭证确认 → PAID + 结算单已付累计回写 +
 * 结算单全部付清 → 订单 {@code PAID}。付款确认无预算动作（核销已在结算完成，
 * 规格 §5 行 12）。</p>
 */
@Service
public class PaymentServiceImpl extends ServiceImpl<PaymentMapper, Payment>
        implements IPaymentService {

    private static final String BIZ_TYPE = "PAYMENT";

    private final ApprovalGateway approvalGateway;

    @Autowired
    private SettlementMapper settlementMapper;

    @Autowired
    private PurchaseOrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private BusinessNoGenerator businessNoGenerator;

    public PaymentServiceImpl(ObjectProvider<ApprovalGateway> gatewayProvider) {
        this.approvalGateway = gatewayProvider.getIfAvailable();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPayment(PaymentSaveReqVO req) {
        Settlement settlement = requireSettlement(req.getSettlementId());
        if (settlement.getStatus() != SettlementStatus.SETTLED) {
            throw new BizException(ResultCode.STATUS_INVALID, "仅已结算（SETTLED）可发起付款");
        }
        BigDecimal paid = baseMapper.sumPaidAmount(settlement.getId());
        BigDecimal payAmount = req.getPayAmount() == null ? BigDecimal.ZERO : req.getPayAmount();
        if (payAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "付款金额必须大于 0");
        }
        if (paid.add(payAmount).compareTo(settlement.getAmount()) > 0) {
            throw new BizException(ResultCode.BIZ_ERROR,
                    "累计付款超出结算金额：已付 " + paid + "，本次 " + payAmount
                            + "，结算 " + settlement.getAmount());
        }
        Payment p = new Payment();
        p.setSettlementId(settlement.getId());
        p.setPayNo(businessNoGenerator.nextNo("FK"));
        p.setPayAmount(payAmount);
        p.setPayMethod(req.getPayMethod());
        p.setRemark(req.getRemark());
        p.setStatus(PaymentStatus.UNPAID);
        save(p);
        return p.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePayment(Long id, PaymentSaveReqVO req) {
        Payment p = requirePayment(id);
        if (p.getStatus() != PaymentStatus.REJECTED && p.getStatus() != PaymentStatus.UNPAID) {
            throw new BizException(ResultCode.STATUS_INVALID, "仅未确认付款可修改");
        }
        if (req.getPayAmount() != null) {
            p.setPayAmount(req.getPayAmount());
        }
        if (req.getPayMethod() != null) {
            p.setPayMethod(req.getPayMethod());
        }
        if (req.getRemark() != null) {
            p.setRemark(req.getRemark());
        }
        if (p.getStatus() == PaymentStatus.REJECTED) {
            p.setStatus(PaymentStatus.UNPAID);
        }
        updateById(p);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(Long id) {
        Payment p = requirePayment(id);
        if (p.getStatus() != PaymentStatus.UNPAID || p.getReviewedAt() != null) {
            throw new BizException(ResultCode.STATUS_INVALID, "仅待财务审付款单可提交");
        }
        if (approvalGateway == null) {
            throw new BizException(ResultCode.BIZ_ERROR, "审批网关不可用");
        }
        ApprovalTaskSpec spec = new ApprovalTaskSpec();
        spec.setBizType(BIZ_TYPE);
        spec.setBizId(p.getId());
        spec.setTitle("付款审批-" + p.getPayNo());
        spec.setApplicant(UserContext.getCurrentUsername());
        JSONObject payload = new JSONObject();
        payload.set("payNo", p.getPayNo());
        payload.set("payAmount", p.getPayAmount());
        payload.set("settlementId", p.getSettlementId());
        spec.setPayloadJson(payload.toString());
        return approvalGateway.create(spec);
    }

    /** PAYMENT 审批通过（由 PaymentApprovalHandler 委托）：财务审核落人落时间，仍待登记确认。 */
    @Transactional(rollbackFor = Exception.class)
    public void handleApproved(Long taskId, Long bizId) {
        Payment p = getById(bizId);
        if (p == null || p.getStatus() != PaymentStatus.UNPAID || p.getReviewedAt() != null) {
            return;
        }
        p.setReviewedBy(UserContext.getCurrentUserId());
        p.setReviewedAt(java.time.LocalDateTime.now());
        updateById(p);
    }

    /** PAYMENT 审批驳回：REJECTED（可修改重提）。 */
    @Transactional(rollbackFor = Exception.class)
    public void handleRejected(Long taskId, Long bizId) {
        Payment p = getById(bizId);
        if (p == null || p.getStatus() != PaymentStatus.UNPAID) {
            return;
        }
        p.setStatus(PaymentStatus.REJECTED);
        updateById(p);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmPayment(Long id, String voucherFile, LocalDate payDate) {
        Payment p = requirePayment(id);
        if (p.getStatus() != PaymentStatus.UNPAID || p.getReviewedAt() == null) {
            throw new BizException(ResultCode.STATUS_INVALID, "仅财务审核通过的付款单可登记确认");
        }
        if (voucherFile == null || voucherFile.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "登记确认必须上传付款凭证");
        }
        p.setVoucherFile(voucherFile);
        p.setPayDate(payDate == null ? LocalDate.now() : payDate);
        p.setConfirmedBy(UserContext.getCurrentUserId());
        p.setConfirmedAt(java.time.LocalDateTime.now());
        p.setStatus(PaymentStatus.PAID);
        updateById(p);
        settleOrderIfFullyPaid(p);
    }

    @Override
    public StatementVO statement(Long supplierId, LocalDate from, LocalDate to) {
        StatementVO vo = new StatementVO();
        vo.setSupplierId(supplierId);
        vo.setFrom(from);
        vo.setTo(to);
        // 应付 = 供应商订单的已结算结算单；已付 = 对应 PAID 付款
        List<PurchaseOrder> orders = orderMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrder>().eq(PurchaseOrder::getSupplierId, supplierId));
        BigDecimal payable = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        for (PurchaseOrder order : orders) {
            List<Settlement> settlements = settlementMapper.selectByOrder(order.getId());
            for (Settlement s : settlements) {
                boolean inRange = (from == null || !s.getCreatedAt().toLocalDate().isBefore(from))
                        && (to == null || !s.getCreatedAt().toLocalDate().isAfter(to));
                if (s.getStatus() == SettlementStatus.SETTLED && inRange) {
                    payable = payable.add(nvl(s.getAmount()));
                    vo.getRows().add(row(s.getCreatedAt().toLocalDate(), s.getSettleNo(),
                            "SETTLEMENT", s.getAmount(), s.getRemark()));
                }
                if (s.getStatus() == SettlementStatus.SETTLED || s.getStatus() == SettlementStatus.PENDING) {
                    for (Payment p : baseMapper.selectBySettlement(s.getId())) {
                        if (p.getStatus() != PaymentStatus.PAID) {
                            continue;
                        }
                        LocalDate d = p.getPayDate() == null
                                ? p.getConfirmedAt().toLocalDate() : p.getPayDate();
                        boolean pInRange = (from == null || !d.isBefore(from))
                                && (to == null || !d.isAfter(to));
                        if (pInRange) {
                            paid = paid.add(nvl(p.getPayAmount()));
                            vo.getRows().add(row(d, p.getPayNo(), "PAYMENT",
                                    p.getPayAmount(), p.getRemark()));
                        }
                    }
                }
            }
        }
        vo.setTotalPayable(payable.setScale(2, RoundingMode.HALF_UP));
        vo.setTotalPaid(paid.setScale(2, RoundingMode.HALF_UP));
        vo.setBalance(payable.subtract(paid).setScale(2, RoundingMode.HALF_UP));
        vo.getRows().sort((a, b) -> a.getDate().compareTo(b.getDate()));
        return vo;
    }

    // ---------------- 内部 ----------------

    /** 结算单全部付清（Σ已付 ≥ Σ该订单已结算金额）→ 订单 PAID。 */
    private void settleOrderIfFullyPaid(Payment payment) {
        Settlement settlement = settlementMapper.selectById(payment.getSettlementId());
        if (settlement == null) {
            return;
        }
        Long orderId = settlement.getOrderId();
        List<Settlement> settlements = settlementMapper.selectByOrder(orderId);
        BigDecimal settledTotal = settlements.stream()
                .filter(s -> s.getStatus() == SettlementStatus.SETTLED)
                .map(s -> nvl(s.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidTotal = settlements.stream()
                .filter(s -> s.getStatus() == SettlementStatus.SETTLED)
                .map(s -> baseMapper.sumPaidAmount(s.getId()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (paidTotal.compareTo(settledTotal) >= 0 && settledTotal.compareTo(BigDecimal.ZERO) > 0) {
            PurchaseOrder order = orderMapper.selectById(orderId);
            if (order != null && order.getStatus() == OrderStatus.SETTLED) {
                order.setStatus(OrderStatus.PAID);
                orderMapper.updateById(order);
            }
        }
    }

    private StatementVO.Row row(LocalDate date, String docNo, String direction,
                                BigDecimal amount, String remark) {
        StatementVO.Row r = new StatementVO.Row();
        r.setDate(date);
        r.setDocNo(docNo);
        r.setDirection(direction);
        r.setAmount(amount);
        r.setRemark(remark);
        return r;
    }

    private Settlement requireSettlement(Long id) {
        Settlement s = settlementMapper.selectById(id);
        if (s == null) {
            throw new BizException(ResultCode.NOT_FOUND, "结算单不存在：" + id);
        }
        return s;
    }

    private Payment requirePayment(Long id) {
        Payment p = getById(id);
        if (p == null) {
            throw new BizException(ResultCode.NOT_FOUND, "付款单不存在：" + id);
        }
        return p;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
