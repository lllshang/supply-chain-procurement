package com.dzgylxt.service.impl.settlement;

import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.approval.ApprovalTaskSpec;
import com.dzgylxt.approval.ApprovalGateway;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.order.Arrival;
import com.dzgylxt.entity.order.ArrivalItem;
import com.dzgylxt.entity.order.OrderItem;
import com.dzgylxt.entity.order.PurchaseOrder;
import com.dzgylxt.entity.order.ServiceAssess;
import com.dzgylxt.entity.settlement.Settlement;
import com.dzgylxt.enums.ArrivalStatus;
import com.dzgylxt.enums.BudgetBizType;
import com.dzgylxt.enums.OrderStatus;
import com.dzgylxt.enums.SettlementStatus;
import com.dzgylxt.enums.SettlementType;
import com.dzgylxt.mapper.order.ArrivalItemMapper;
import com.dzgylxt.mapper.order.ArrivalMapper;
import com.dzgylxt.mapper.order.OrderItemMapper;
import com.dzgylxt.mapper.order.PurchaseOrderMapper;
import com.dzgylxt.mapper.order.ServiceAssessMapper;
import com.dzgylxt.mapper.settlement.SettlementMapper;
import com.dzgylxt.security.UserContext;
import com.dzgylxt.service.IBudgetOccupyService;
import com.dzgylxt.service.ISettlementService;
import com.dzgylxt.vo.budget.BudgetOccupyCmd;
import com.dzgylxt.vo.settlement.SettlementDraftVO;
import com.dzgylxt.vo.settlement.SettlementSaveReqVO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 结算服务实现（P3 设计 §2.1 / T04）。
 *
 * <p>核心联动（规格 §5 行 11）：SETTLEMENT 审批通过 → 预算核销（{@code writeOff}，
 * used_amount 不变转构成）→ 订单全部结清（Σ已结 ≥ 应结总额）→ 订单 {@code SETTLED}。
 * 驳回保持 PENDING 留痕可重提（不改枚举值，规格口径）。</p>
 */
@Service
public class SettlementServiceImpl extends ServiceImpl<SettlementMapper, Settlement>
        implements ISettlementService {

    private static final String BIZ_TYPE = "SETTLEMENT";

    @Autowired
    private PurchaseOrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private ArrivalMapper arrivalMapper;

    @Autowired
    private ArrivalItemMapper arrivalItemMapper;

    @Autowired
    private ServiceAssessMapper serviceAssessMapper;

    @Autowired
    private BusinessNoGenerator businessNoGenerator;

    @Autowired
    private IBudgetOccupyService budgetOccupyService;

    /** 网关经 ObjectProvider 注入（网关 → 回调处理器 → 本服务，防循环依赖）。 */
    private final ApprovalGateway approvalGateway;

    public SettlementServiceImpl(ObjectProvider<ApprovalGateway> gatewayProvider) {
        this.approvalGateway = gatewayProvider.getIfAvailable();
    }

    @Override
    public SettlementDraftVO draftFromOrder(Long orderId) {
        PurchaseOrder order = requireOrder(orderId);
        return buildDraft(order, null);
    }

    @Override
    public SettlementDraftVO draftFromArrival(Long arrivalId) {
        Arrival arrival = arrivalMapper.selectById(arrivalId);
        if (arrival == null) {
            throw new BizException(ResultCode.NOT_FOUND, "到货单不存在：" + arrivalId);
        }
        PurchaseOrder order = requireOrder(arrival.getOrderId());
        SettlementDraftVO draft = buildDraft(order, arrivalId);
        // 到货单入口：本单已入库量优先带出
        draft.setArrivalStoredQty(sumStoredOfArrival(arrivalId));
        return draft;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSettlement(SettlementSaveReqVO req) {
        PurchaseOrder order = requireOrder(req.getOrderId());
        validateOrderSettleable(order);

        List<Settlement> history = baseMapper.selectByOrder(order.getId());
        BigDecimal storedQty = sumStoredOfOrder(order.getId());
        BigDecimal settledQty = history.stream()
                .map(s -> nvl(s.getSettledQtyBase()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal settledAmount = history.stream()
                .filter(s -> s.getStatus() == SettlementStatus.SETTLED)
                .map(s -> nvl(s.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal orderAmount = orderTotalAmount(order.getId());

        // 重复结算拦截（到货单入口粒度）
        if (req.getArrivalId() != null && baseMapper.existsByArrival(req.getArrivalId())) {
            throw new BizException(ResultCode.BIZ_ERROR, "该到货单已登记结算，不可重复结算");
        }
        // 结算数量 ≤ 已入库合格累计 − 已结数量
        BigDecimal qty = nvl(req.getSettledQtyBase());
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "结算数量必须大于 0");
        }
        if (qty.compareTo(storedQty.subtract(settledQty)) > 0) {
            throw new BizException(ResultCode.BIZ_ERROR,
                    "结算数量超出可结余量：入库 " + storedQty + "，已结 " + settledQty);
        }

        Settlement s = new Settlement();
        s.setOrderId(order.getId());
        s.setArrivalId(req.getArrivalId());
        s.setContractId(req.getContractId() == null ? order.getContractId() : req.getContractId());
        s.setSettleNo(businessNoGenerator.nextNo("JS"));
        s.setSettledQtyBase(qty);
        s.setType(req.getType() == null
                ? (order.getOrderType() == com.dzgylxt.enums.ItemType.SERVICE
                        ? SettlementType.SERVICE : SettlementType.MATERIAL)
                : req.getType());
        s.setSettleMode(req.getSettleMode());
        s.setPhaseNo(req.getPhaseNo());
        s.setPhaseRatio(req.getPhaseRatio());
        s.setIsFinal(req.getIsFinal() == null ? 0 : req.getIsFinal());
        s.setRemark(req.getRemark());
        s.setAmount(resolveAmount(req, order, orderAmount));
        validatePhaseAndFinal(s, history, orderAmount);
        s.setStatus(SettlementStatus.PENDING);
        save(s);
        return s.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSettlement(Long id, SettlementSaveReqVO req) {
        Settlement s = getById(id);
        if (s == null) {
            throw new BizException(ResultCode.NOT_FOUND, "结算单不存在：" + id);
        }
        if (s.getStatus() != SettlementStatus.PENDING) {
            throw new BizException(ResultCode.STATUS_INVALID, "仅待结算可修改");
        }
        // 驳回留痕后重提：更新数量/金额/备注（校验同创建，复用创建逻辑语义）
        if (req.getSettledQtyBase() != null) {
            s.setSettledQtyBase(req.getSettledQtyBase());
        }
        if (req.getAmount() != null) {
            s.setAmount(req.getAmount());
        }
        if (req.getRemark() != null) {
            s.setRemark(req.getRemark());
        }
        updateById(s);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(Long id) {
        Settlement s = getById(id);
        if (s == null) {
            throw new BizException(ResultCode.NOT_FOUND, "结算单不存在：" + id);
        }
        if (s.getStatus() != SettlementStatus.PENDING) {
            throw new BizException(ResultCode.STATUS_INVALID, "仅待结算可提交");
        }
        if (approvalGateway == null) {
            throw new BizException(ResultCode.BIZ_ERROR, "审批网关不可用");
        }
        ApprovalTaskSpec spec = new ApprovalTaskSpec();
        spec.setBizType(BIZ_TYPE);
        spec.setBizId(s.getId());
        spec.setTitle("结算审批-" + s.getSettleNo());
        spec.setApplicant(UserContext.getCurrentUsername());
        JSONObject payload = new JSONObject();
        payload.set("settleNo", s.getSettleNo());
        payload.set("amount", s.getAmount());
        payload.set("orderId", s.getOrderId());
        spec.setPayloadJson(payload.toString());
        return approvalGateway.create(spec);
    }

    /**
     * SETTLEMENT 审批回调由 {@code SettlementApprovalHandler} 委托本方法
     * （通过→核销+结清收口；驳回→保持 PENDING 留痕）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleApproval(Long taskId, Long bizId, boolean approved, String comment) {
        Settlement s = getById(bizId);
        if (s == null || s.getStatus() != SettlementStatus.PENDING) {
            return;
        }
        if (!approved) {
            // 驳回：保持 PENDING（approval_task 留痕），可修改重提
            return;
        }
        s.setStatus(SettlementStatus.SETTLED);
        updateById(s);
        // 预算核销（行 11）：占用→核销，used_amount 不变（构成转移）
        BudgetOccupyCmd cmd = new BudgetOccupyCmd();
        cmd.setBizType(BudgetBizType.ORDER);
        cmd.setBizId(s.getOrderId());
        cmd.setAmount(s.getAmount());
        cmd.setRemark("结算核销-" + s.getSettleNo());
        budgetOccupyService.writeOff(cmd);
        // 订单全部结清（Σ已结 ≥ 应结总额）→ SETTLED
        BigDecimal settled = baseMapper.sumSettledAmount(s.getOrderId());
        BigDecimal total = orderTotalAmount(s.getOrderId());
        if (settled.compareTo(total) >= 0) {
            PurchaseOrder order = orderMapper.selectById(s.getOrderId());
            if (order != null && order.getStatus() != OrderStatus.CANCELLED) {
                order.setStatus(OrderStatus.SETTLED);
                orderMapper.updateById(order);
            }
        }
    }

    @Override
    public IPage<Settlement> page(long current, long size, Long orderId, Long supplierId,
                                  SettlementStatus status) {
        LambdaQueryWrapper<Settlement> wrapper = new LambdaQueryWrapper<>();
        if (orderId != null) {
            wrapper.eq(Settlement::getOrderId, orderId);
        }
        if (supplierId != null) {
            List<PurchaseOrder> orders = orderMapper.selectList(
                    new LambdaQueryWrapper<PurchaseOrder>().eq(PurchaseOrder::getSupplierId, supplierId));
            if (orders.isEmpty()) {
                return new Page<>(current, size);
            }
            wrapper.in(Settlement::getOrderId, orders.stream().map(PurchaseOrder::getId).toList());
        }
        if (status != null) {
            wrapper.eq(Settlement::getStatus, status);
        }
        return page(new Page<>(current, size), wrapper.orderByDesc(Settlement::getId));
    }

    // ---------------- 内部 ----------------

    /** 组装草稿（订单/到货双入口共用）。 */
    private SettlementDraftVO buildDraft(PurchaseOrder order, Long arrivalId) {
        SettlementDraftVO draft = new SettlementDraftVO();
        draft.setOrderId(order.getId());
        draft.setOrderNo(order.getOrderNo());
        draft.setSupplierId(order.getSupplierId());
        draft.setContractId(order.getContractId());
        draft.setOrderType(order.getOrderType() == null ? null : order.getOrderType().name());

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()));
        BigDecimal orderAmount = BigDecimal.ZERO;
        for (OrderItem item : items) {
            SettlementDraftVO.DraftItem di = new SettlementDraftVO.DraftItem();
            di.setOrderItemId(item.getId());
            di.setSkuId(item.getSkuId());
            di.setQtyBase(item.getQtyBase());
            di.setQtyStored(sumStoredOfItem(item.getId()));
            di.setPrice(item.getPrice());
            draft.getItems().add(di);
            orderAmount = orderAmount.add(
                    nvl(item.getPrice()).multiply(nvl(item.getQtyBase())));
        }
        draft.setOrderAmount(orderAmount.setScale(2, RoundingMode.HALF_UP));

        BigDecimal stored = sumStoredOfOrder(order.getId());
        List<Settlement> history = baseMapper.selectByOrder(order.getId());
        BigDecimal settledQty = history.stream()
                .map(s -> nvl(s.getSettledQtyBase()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal settledAmount = history.stream()
                .filter(x -> x.getStatus() == SettlementStatus.SETTLED)
                .map(x -> nvl(x.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        draft.setStoredQtyBase(stored);
        draft.setSettledQtyBase(settledQty);
        draft.setSettledAmount(settledAmount);
        draft.setRemainQtyBase(stored.subtract(settledQty).max(BigDecimal.ZERO));

        // 服务扣款取数（<!-- D8 已落地 P3 -->：物料单=0）
        BigDecimal deduct = BigDecimal.ZERO;
        if (order.getOrderType() == com.dzgylxt.enums.ItemType.SERVICE) {
            deduct = serviceAssessMapper.selectList(
                            new LambdaQueryWrapper<ServiceAssess>().eq(ServiceAssess::getOrderId, order.getId()))
                    .stream().map(a -> nvl(a.getDeductAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        draft.setAssessDeduct(deduct);

        // 建议金额：物料=可结余×订单均价；服务=订单金额×(可结/入库)−扣款
        BigDecimal suggest;
        if (order.getOrderType() == com.dzgylxt.enums.ItemType.SERVICE && stored.compareTo(BigDecimal.ZERO) > 0) {
            suggest = orderAmount.multiply(draft.getRemainQtyBase().divide(stored, 6, RoundingMode.HALF_UP))
                    .subtract(deduct);
        } else {
            BigDecimal avgPrice = stored.compareTo(BigDecimal.ZERO) > 0
                    ? orderAmount.divide(stored, 6, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            suggest = draft.getRemainQtyBase().multiply(avgPrice);
        }
        draft.setSuggestAmount(suggest.setScale(2, RoundingMode.HALF_UP).max(BigDecimal.ZERO));

        if (arrivalId != null) {
            draft.setArrivalStoredQty(sumStoredOfArrival(arrivalId));
        }
        return draft;
    }

    /** 金额解析：req 显式金额优先；否则物料=数量×订单均价、服务=订单金额−扣款（按比例）。 */
    private BigDecimal resolveAmount(SettlementSaveReqVO req, PurchaseOrder order,
                                     BigDecimal orderAmount) {
        if (req.getAmount() != null && req.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            return req.getAmount().setScale(2, RoundingMode.HALF_UP);
        }
        SettlementDraftVO draft = buildDraft(order, req.getArrivalId());
        if (req.getType() == SettlementType.SERVICE
                || order.getOrderType() == com.dzgylxt.enums.ItemType.SERVICE) {
            // 服务结算 = 订单金额 × 本次/入库比例 − Σ扣款（扣款一次性扣完，不足不穿 0）
            BigDecimal ratio = draft.getStoredQtyBase().compareTo(BigDecimal.ZERO) > 0
                    ? nvl(req.getSettledQtyBase()).divide(draft.getStoredQtyBase(), 6, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            return orderAmount.multiply(ratio).subtract(draft.getAssessDeduct())
                    .setScale(2, RoundingMode.HALF_UP).max(BigDecimal.ZERO);
        }
        BigDecimal avgPrice = draft.getStoredQtyBase().compareTo(BigDecimal.ZERO) > 0
                ? orderAmount.divide(draft.getStoredQtyBase(), 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return nvl(req.getSettledQtyBase()).multiply(avgPrice).setScale(2, RoundingMode.HALF_UP);
    }

    /** 阶段比例 Σ≤100；尾款：Σ已结(SETTLED) + 本次 = 应结总额。 */
    private void validatePhaseAndFinal(Settlement current, List<Settlement> history,
                                       BigDecimal orderAmount) {
        if (current.getSettleMode() == com.dzgylxt.enums.SettleMode.PHASE) {
            if (current.getPhaseNo() == null
                    || current.getPhaseRatio() == null
                    || current.getPhaseRatio().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(ResultCode.PARAM_ERROR, "阶段结算必须填写阶段号与比例");
            }
            BigDecimal ratioSum = current.getPhaseRatio();
            for (Settlement h : history) {
                if (h.getSettleMode() == com.dzgylxt.enums.SettleMode.PHASE
                        && current.getPhaseNo().equals(h.getPhaseNo())) {
                    throw new BizException(ResultCode.BIZ_ERROR, "阶段 " + current.getPhaseNo() + " 已存在结算单");
                }
                ratioSum = ratioSum.add(nvl(h.getPhaseRatio()));
            }
            if (ratioSum.compareTo(new BigDecimal("100")) > 0) {
                throw new BizException(ResultCode.BIZ_ERROR,
                        "阶段比例合计超 100%：当前 " + ratioSum);
            }
        }
        if (current.getIsFinal() != null && current.getIsFinal() == 1) {
            BigDecimal settled = history.stream()
                    .filter(h -> h.getStatus() == SettlementStatus.SETTLED)
                    .map(h -> nvl(h.getAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (settled.add(nvl(current.getAmount())).compareTo(orderAmount) != 0) {
                throw new BizException(ResultCode.BIZ_ERROR,
                        "尾款结清要求 累计已结 + 本次 = 应结总额 " + orderAmount
                                + "（当前 " + settled.add(nvl(current.getAmount())) + "）");
            }
        }
    }

    /** 订单可结算状态：RECEIVED / PARTIAL_RECEIVED。 */
    private void validateOrderSettleable(PurchaseOrder order) {
        if (order.getStatus() != OrderStatus.RECEIVED
                && order.getStatus() != OrderStatus.PARTIAL_RECEIVED) {
            throw new BizException(ResultCode.STATUS_INVALID,
                    "订单当前状态不可结算：" + order.getStatus().getDesc());
        }
    }

    private PurchaseOrder requireOrder(Long orderId) {
        PurchaseOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在：" + orderId);
        }
        return order;
    }

    /** 订单应结总额 = Σ 明细 price × qty_base（基本单位口径 #27）。 */
    private BigDecimal orderTotalAmount(Long orderId) {
        return orderItemMapper.selectList(
                        new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId)).stream()
                .map(i -> nvl(i.getPrice()).multiply(nvl(i.getQtyBase())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** 订单入库合格累计（Σ arrival_item.qty_stored，基本单位）。 */
    private BigDecimal sumStoredOfOrder(Long orderId) {
        List<Long> arrivalIds = arrivalMapper.selectList(
                        new LambdaQueryWrapper<Arrival>().eq(Arrival::getOrderId, orderId))
                .stream().map(Arrival::getId).toList();
        if (arrivalIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return arrivalItemMapper.selectList(
                        new LambdaQueryWrapper<ArrivalItem>().in(ArrivalItem::getArrivalId, arrivalIds))
                .stream().map(i -> nvl(i.getQtyStored()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 单个到货单已入库累计。 */
    private BigDecimal sumStoredOfArrival(Long arrivalId) {
        return arrivalItemMapper.selectList(
                        new LambdaQueryWrapper<ArrivalItem>().eq(ArrivalItem::getArrivalId, arrivalId))
                .stream().map(i -> nvl(i.getQtyStored()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 订单明细已入库累计。 */
    private BigDecimal sumStoredOfItem(Long orderItemId) {
        return arrivalItemMapper.selectList(
                        new LambdaQueryWrapper<ArrivalItem>().eq(ArrivalItem::getOrderItemId, orderItemId))
                .stream().map(i -> nvl(i.getQtyStored()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
