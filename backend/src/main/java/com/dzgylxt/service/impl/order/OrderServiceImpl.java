package com.dzgylxt.service.impl.order;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.approval.ApprovalGateway;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.common.RedisLockUtil;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.catalog.UnitConversion;
import com.dzgylxt.entity.contract.Contract;
import com.dzgylxt.entity.order.Arrival;
import com.dzgylxt.entity.order.OrderChange;
import com.dzgylxt.entity.order.OrderItem;
import com.dzgylxt.entity.order.PurchaseOrder;
import com.dzgylxt.entity.purchase.Inquiry;
import com.dzgylxt.entity.purchase.Award;
import com.dzgylxt.entity.purchase.AwardItem;
import com.dzgylxt.entity.purchase.PurchaseApply;
import com.dzgylxt.entity.purchase.PurchaseApplyItem;
import com.dzgylxt.enums.ChangeStatus;
import com.dzgylxt.enums.ChangeType;
import com.dzgylxt.enums.ContractStatus;
import com.dzgylxt.enums.ItemType;
import com.dzgylxt.enums.OrderStatus;
import com.dzgylxt.enums.PurchaseApplyStatus;
import com.dzgylxt.mapper.catalog.UnitConversionMapper;
import com.dzgylxt.mapper.contract.ContractMapper;
import com.dzgylxt.mapper.order.ArrivalItemMapper;
import com.dzgylxt.mapper.order.ArrivalMapper;
import com.dzgylxt.mapper.order.OrderChangeMapper;
import com.dzgylxt.mapper.order.OrderItemMapper;
import com.dzgylxt.mapper.order.PurchaseOrderMapper;
import com.dzgylxt.mapper.purchase.AwardItemMapper;
import com.dzgylxt.mapper.purchase.AwardMapper;
import com.dzgylxt.mapper.purchase.InquiryMapper;
import com.dzgylxt.mapper.purchase.PurchaseApplyItemMapper;
import com.dzgylxt.mapper.purchase.PurchaseApplyMapper;
import com.dzgylxt.security.UserContext;
import com.dzgylxt.service.IOrderService;
import com.dzgylxt.vo.order.OrderChangeReqVO;
import com.dzgylxt.vo.order.OrderCreateReqVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购订单服务实现（设计 §4 下单三重校验事务 = 合规硬规则）。
 *
 * <p>事务边界与锁（§4.2，顺序全局固定 {@code contract → apply_item} 防死锁）：</p>
 * <ol>
 *   <li>Redis 锁 {@code contract:{cid}} / {@code apply:{aid}}（多实例双保险，3s 快速失败）；</li>
 *   <li>{@code contract} 行锁 {@code FOR UPDATE} → 校验① EFFECTIVE + 有效期；</li>
 *   <li>{@code purchase_apply_item} 行锁（IN 列表排序后加锁）→ 校验③余量 → 条件扣减（version 兜底）；</li>
 *   <li>校验②额度 → 条件扣减 available_amount（version 兜底，扣减式）；</li>
 *   <li>锁内取换算快照（selectCurrentEffective，应用时钟）→ 落单（物料/服务拆单）；</li>
 *   <li>finally 逆序释放 Redis 锁。</li>
 * </ol>
 */
@Service
public class OrderServiceImpl extends ServiceImpl<PurchaseOrderMapper, PurchaseOrder>
        implements IOrderService {

    private static final long LOCK_WAIT_MILLIS = 3000L;

    @Autowired
    private ContractMapper contractMapper;

    @Autowired
    private PurchaseApplyItemMapper applyItemMapper;

    @Autowired
    private PurchaseApplyMapper applyMapper;

    @Autowired
    private InquiryMapper inquiryMapper;

    @Autowired
    private AwardMapper awardMapper;

    @Autowired
    private AwardItemMapper awardItemMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private OrderChangeMapper orderChangeMapper;

    @Autowired
    private ArrivalMapper arrivalMapper;

    @Autowired
    private ArrivalItemMapper arrivalItemMapper;

    @Autowired
    private UnitConversionMapper unitConversionMapper;

    @Autowired
    private RedisLockUtil redisLockUtil;

    @Autowired
    private BusinessNoGenerator businessNoGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> createOrder(OrderCreateReqVO req) {
        if (req.getContractId() == null || req.getApplyId() == null
                || req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "合同/申请/明细均必填");
        }
        for (OrderCreateReqVO.OrderItemReqVO item : req.getItems()) {
            if (item.getApplyItemId() == null || item.getSkuId() == null
                    || item.getQty() == null || item.getQty().compareTo(BigDecimal.ZERO) <= 0
                    || item.getPrice() == null || item.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException(ResultCode.PARAM_ERROR, "下单明细行的申请明细/SKU/数量/单价非法");
            }
        }

        // ① Redis 锁（顺序固定 contract → apply；获取失败快速失败）
        String tokenC = redisLockUtil.tryLock("contract:" + req.getContractId(), LOCK_WAIT_MILLIS);
        if (tokenC == null) {
            throw new BizException(ResultCode.BIZ_ERROR, "订单提交繁忙，请稍后重试");
        }
        String tokenA = redisLockUtil.tryLock("apply:" + req.getApplyId(), LOCK_WAIT_MILLIS);
        if (tokenA == null) {
            redisLockUtil.unlock("contract:" + req.getContractId(), tokenC);
            throw new BizException(ResultCode.BIZ_ERROR, "订单提交繁忙，请稍后重试");
        }
        try {
            // ② contract 行锁（第一把行锁）
            Contract contract = contractMapper.selectForUpdate(req.getContractId());
            // 校验①：合同 EFFECTIVE 且在有效期
            checkContractValid(contract);

            Long supplierId = req.getSupplierId() == null ? contract.getSupplierId() : req.getSupplierId();

            // ③ apply_item 行锁（调用方排序去重，配合主键 IN 扫描保证锁序一致，杜绝交叉死锁）
            List<Long> itemIds = req.getItems().stream()
                    .map(OrderCreateReqVO.OrderItemReqVO::getApplyItemId)
                    .distinct().sorted().toList();
            List<PurchaseApplyItem> lockedItems = applyItemMapper.selectForUpdateByIds(itemIds);
            Map<Long, PurchaseApplyItem> itemMap = new LinkedHashMap<>();
            for (PurchaseApplyItem locked : lockedItems) {
                itemMap.put(locked.getId(), locked);
            }
            for (OrderCreateReqVO.OrderItemReqVO item : req.getItems()) {
                if (!itemMap.containsKey(item.getApplyItemId())) {
                    throw new BizException(ResultCode.PARAM_ERROR, "申请明细不存在：" + item.getApplyItemId());
                }
            }

            // 逐明细：锁内取换算快照 + 余量校验与条件扣减（version 兜底，失败重试 1 次）
            List<OrderLine> lines = new ArrayList<>();
            for (OrderCreateReqVO.OrderItemReqVO item : req.getItems()) {
                PurchaseApplyItem applyItem = itemMap.get(item.getApplyItemId());
                LocalDateTime now = LocalDateTime.now();
                String unit = item.getPurchaseUnit() == null || item.getPurchaseUnit().isBlank()
                        ? applyItem.getPurchaseUnit() : item.getPurchaseUnit();
                UnitConversion conv = unit == null || unit.isBlank() ? null
                        : unitConversionMapper.selectCurrentEffective(item.getSkuId(), unit, now);
                BigDecimal rate = conv == null || conv.getRate() == null ? BigDecimal.ONE : conv.getRate();
                BigDecimal qtyBase = item.getQty().multiply(rate);

                // 校验③：ordered_qty + 本单数量 ≤ apply_qty
                if (applyItem.getOrderedQty().add(qtyBase).compareTo(applyItem.getApplyQty()) > 0) {
                    throw new BizException(ResultCode.BIZ_ERROR,
                            "超出申请余量：明细 " + applyItem.getId()
                                    + " 余量 " + applyItem.getRemainQty() + "，本单 " + qtyBase);
                }
                int updated = applyItemMapper.deductRemain(applyItem.getId(), qtyBase, applyItem.getVersion());
                if (updated == 0) {
                    // 乐观版本兜底：冲突重试 1 次（重读版本）
                    PurchaseApplyItem fresh = applyItemMapper.selectById(applyItem.getId());
                    updated = applyItemMapper.deductRemain(applyItem.getId(), qtyBase, fresh.getVersion());
                }
                if (updated == 0) {
                    throw new BizException(ResultCode.BIZ_ERROR, "申请余量并发冲突，请重试");
                }
                applyItem.setOrderedQty(applyItem.getOrderedQty().add(qtyBase));
                applyItem.setRemainQty(applyItem.getRemainQty().subtract(qtyBase));

                OrderLine line = new OrderLine();
                line.req = item;
                line.applyItem = applyItem;
                line.purchaseUnit = unit;
                line.rate = rate;
                line.qtyBase = qtyBase;
                line.amount = item.getPrice().multiply(qtyBase).setScale(2, java.math.RoundingMode.HALF_UP);
                lines.add(line);
            }

            // 校验②：已用额度 + 本单金额 ≤ 合同 amount（扣减式：available_amount -= 本单金额）
            BigDecimal totalAmount = lines.stream().map(l -> l.amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int deducted = contractMapper.deductAvailable(contract.getId(), totalAmount, contract.getVersion());
            if (deducted == 0) {
                Contract fresh = contractMapper.selectById(contract.getId());
                deducted = contractMapper.deductAvailable(contract.getId(), totalAmount, fresh.getVersion());
            }
            if (deducted == 0) {
                throw new BizException(ResultCode.BIZ_ERROR,
                        "超出合同可用额度：可用 " + contract.getAvailableAmount() + "，本单 " + totalAmount);
            }
            contract.setAvailableAmount(contract.getAvailableAmount().subtract(totalAmount));

            // 落单：物料/服务按 item_type 拆单（设计 §2.6）
            List<Long> orderIds = new ArrayList<>();
            for (ItemType type : new ItemType[]{ItemType.MATERIAL, ItemType.SERVICE}) {
                List<OrderLine> group = lines.stream()
                        .filter(l -> itemTypeOf(l.applyItem) == type).toList();
                if (group.isEmpty()) {
                    continue;
                }
                orderIds.add(insertOrder(req, contract, supplierId, type, group));
            }
            contractMapper.updateById(contract);

            // 申请状态回写：APPROVED → PARTIAL_ORDER / FULL_ORDER
            updateApplyStatusAfterDeduct(req.getApplyId());
            return orderIds;
        } finally {
            // 逆序释放 Redis 锁
            redisLockUtil.unlock("apply:" + req.getApplyId(), tokenA);
            redisLockUtil.unlock("contract:" + req.getContractId(), tokenC);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "取消原因必填");
        }
        PurchaseOrder order = getById(id);
        if (order == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "订单不存在：" + id);
        }
        if (order.getStatus() != OrderStatus.CREATED
                && order.getStatus() != OrderStatus.PARTIAL_RECEIVED) {
            throw new BizException(ResultCode.STATUS_INVALID, "当前状态不允许取消：" + order.getStatus().getDesc());
        }

        String tokenC = redisLockUtil.tryLock("contract:" + order.getContractId(), LOCK_WAIT_MILLIS);
        if (tokenC == null) {
            throw new BizException(ResultCode.BIZ_ERROR, "订单提交繁忙，请稍后重试");
        }
        String tokenA = order.getApplyId() == null ? null
                : redisLockUtil.tryLock("apply:" + order.getApplyId(), LOCK_WAIT_MILLIS);
        try {
            Contract contract = contractMapper.selectForUpdate(order.getContractId());
            checkContractRow(contract);

            List<OrderItem> items = orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                    .eq(OrderItem::getOrderId, id));
            BigDecimal orderAmount = items.stream()
                    .map(i -> i.getPrice() == null || i.getQtyBase() == null
                            ? BigDecimal.ZERO : i.getPrice().multiply(i.getQtyBase()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, java.math.RoundingMode.HALF_UP);

            // 释放合同额度（扣减式回冲，version 兜底）
            if (orderAmount.compareTo(BigDecimal.ZERO) > 0) {
                contractMapper.releaseAvailable(contract.getId(), orderAmount, contract.getVersion());
            }
            // 回冲申请余量（先锁申请明细行，再按真实 version 条件回冲）
            List<PurchaseApplyItem> lockedApplyItems = order.getApplyId() == null
                    ? List.of() : applyItemMapper.selectForUpdateByApply(order.getApplyId());
            Map<Long, PurchaseApplyItem> applyItemMap = new LinkedHashMap<>();
            for (PurchaseApplyItem locked : lockedApplyItems) {
                applyItemMap.put(locked.getId(), locked);
            }
            for (OrderItem item : items) {
                if (item.getApplyItemId() != null && item.getQtyBase() != null
                        && item.getQtyBase().compareTo(BigDecimal.ZERO) > 0) {
                    PurchaseApplyItem applyItem = applyItemMap.get(item.getApplyItemId());
                    if (applyItem != null) {
                        applyItemMapper.restoreRemain(item.getApplyItemId(), item.getQtyBase(),
                                applyItem.getVersion());
                    }
                }
            }

            // 取消留痕（change_type=4，免审直接生效 Q5）
            OrderChange change = new OrderChange();
            change.setOrderId(id);
            change.setChangeType(ChangeType.CANCEL);
            change.setBeforeJson(JSONUtil.toJsonStr(Map.of(
                    "status", order.getStatus() == null ? "" : order.getStatus().getDesc(),
                    "amount", orderAmount)));
            change.setAfterJson(JSONUtil.toJsonStr(Map.of("status", "CANCELLED")));
            change.setReason(reason);
            change.setStatus(ChangeStatus.EFFECTIVE);
            change.setOperator(UserContext.getCurrentUserId());
            orderChangeMapper.insert(change);

            order.setStatus(OrderStatus.CANCELLED);
            updateById(order);

            // 申请状态回写：FULL_ORDER 回退 PARTIAL_ORDER / APPROVED
            if (order.getApplyId() != null) {
                updateApplyStatusAfterRestore(order.getApplyId());
            }
        } finally {
            if (tokenA != null) {
                redisLockUtil.unlock("apply:" + order.getApplyId(), tokenA);
            }
            redisLockUtil.unlock("contract:" + order.getContractId(), tokenC);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeOrder(Long id, OrderChangeReqVO req) {
        if (req == null || req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "变更明细不能为空");
        }
        PurchaseOrder order = getById(id);
        if (order == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "订单不存在：" + id);
        }
        // Q9：仅 CREATED（未到货）可变更；PARTIAL_RECEIVED 后走履约调整
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BizException(ResultCode.STATUS_INVALID, "仅已创建订单可变更，请走履约调整");
        }

        String tokenC = redisLockUtil.tryLock("contract:" + order.getContractId(), LOCK_WAIT_MILLIS);
        if (tokenC == null) {
            throw new BizException(ResultCode.BIZ_ERROR, "订单提交繁忙，请稍后重试");
        }
        String tokenA = order.getApplyId() == null ? null
                : redisLockUtil.tryLock("apply:" + order.getApplyId(), LOCK_WAIT_MILLIS);
        try {
            Contract contract = contractMapper.selectForUpdate(order.getContractId());
            checkContractValid(contract);

            List<OrderItem> items = orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                    .eq(OrderItem::getOrderId, id));
            Map<Long, OrderItem> itemMap = new LinkedHashMap<>();
            for (OrderItem item : items) {
                itemMap.put(item.getId(), item);
            }
            // before 快照
            String beforeJson = JSONUtil.toJsonStr(items);

            BigDecimal amountDelta = BigDecimal.ZERO;
            for (OrderChangeReqVO.ItemChange change : req.getItems()) {
                OrderItem item = itemMap.get(change.getOrderItemId());
                if (item == null) {
                    throw new BizException(ResultCode.PARAM_ERROR, "订单明细不存在：" + change.getOrderItemId());
                }
                JSONObject conv = JSONUtil.parseObj(item.getConvSnapshot() == null ? "{}" : item.getConvSnapshot());
                BigDecimal rate = conv.getBigDecimal("rate") == null ? BigDecimal.ONE : conv.getBigDecimal("rate");

                BigDecimal oldAmount = item.getPrice().multiply(item.getQtyBase());
                if (change.getNewQty() != null && change.getNewQty().compareTo(item.getQtyPurchase()) != 0) {
                    BigDecimal newBase = change.getNewQty().multiply(rate);
                    BigDecimal baseDelta = newBase.subtract(item.getQtyBase());
                    // 数量差额重跑余量校验（规则③）：增加需有足够余量
                    if (baseDelta.compareTo(BigDecimal.ZERO) > 0 && item.getApplyItemId() != null) {
                        PurchaseApplyItem applyItem = applyItemMapper.selectForUpdateByIds(
                                List.of(item.getApplyItemId())).get(0);
                        if (applyItem.getRemainQty().compareTo(baseDelta) < 0) {
                            throw new BizException(ResultCode.BIZ_ERROR,
                                    "变更超出申请余量：明细 " + applyItem.getId()
                                            + " 余量 " + applyItem.getRemainQty() + "，需 " + baseDelta);
                        }
                        int updated = applyItemMapper.deductRemain(applyItem.getId(), baseDelta, applyItem.getVersion());
                        if (updated == 0) {
                            throw new BizException(ResultCode.BIZ_ERROR, "申请余量并发冲突，请重试");
                        }
                    } else if (baseDelta.compareTo(BigDecimal.ZERO) < 0 && item.getApplyItemId() != null) {
                        PurchaseApplyItem applyItem = applyItemMapper.selectForUpdateByIds(
                                List.of(item.getApplyItemId())).get(0);
                        applyItemMapper.restoreRemain(item.getApplyItemId(), baseDelta.abs(),
                                applyItem.getVersion());
                    }
                    item.setQtyPurchase(change.getNewQty());
                    item.setQtyBase(newBase);
                }
                if (change.getNewPrice() != null) {
                    item.setPrice(change.getNewPrice());
                }
                amountDelta = amountDelta.add(item.getPrice().multiply(item.getQtyBase()).subtract(oldAmount));
            }

            // 金额差额重跑额度校验（规则②）：正差额需有足够额度
            amountDelta = amountDelta.setScale(2, java.math.RoundingMode.HALF_UP);
            if (amountDelta.compareTo(BigDecimal.ZERO) > 0) {
                int updated = contractMapper.deductAvailable(contract.getId(), amountDelta, contract.getVersion());
                if (updated == 0) {
                    throw new BizException(ResultCode.BIZ_ERROR, "变更超出合同可用额度");
                }
            } else if (amountDelta.compareTo(BigDecimal.ZERO) < 0) {
                contractMapper.releaseAvailable(contract.getId(), amountDelta.abs(), contract.getVersion());
            }
            if (order.getBudgetOccupied() != null) {
                order.setBudgetOccupied(order.getBudgetOccupied().add(amountDelta));
            }
            for (OrderItem item : items) {
                orderItemMapper.updateById(item);
            }

            // after 快照 + 留痕（Q5：免审直接生效）
            String afterJson = JSONUtil.toJsonStr(orderItemMapper.selectList(
                    Wrappers.<OrderItem>lambdaQuery().eq(OrderItem::getOrderId, id)));
            OrderChange change = new OrderChange();
            change.setOrderId(id);
            change.setChangeType(ChangeType.QTY);
            change.setBeforeJson(beforeJson);
            change.setAfterJson(afterJson);
            change.setReason(req.getReason());
            change.setStatus(ChangeStatus.EFFECTIVE);
            change.setOperator(UserContext.getCurrentUserId());
            orderChangeMapper.insert(change);
            updateById(order);
        } finally {
            if (tokenA != null) {
                redisLockUtil.unlock("apply:" + order.getApplyId(), tokenA);
            }
            redisLockUtil.unlock("contract:" + order.getContractId(), tokenC);
        }
    }

    @Override
    public List<OrderItem> listItems(Long orderId) {
        return orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, orderId).orderByAsc(OrderItem::getId));
    }

    /** 全链路追溯：申请↔询价↔定标↔合同↔订单↔到货（设计 §2.6 trace）。 */
    @Override
    public Object trace(Long id) {
        PurchaseOrder order = getById(id);
        if (order == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "订单不存在：" + id);
        }
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("order", order);
        trace.put("orderItems", listItems(id));
        trace.put("contract", contractMapper.selectById(order.getContractId()));
        if (order.getApplyId() != null) {
            trace.put("apply", applyMapper.selectById(order.getApplyId()));
            trace.put("applyItems", applyItemMapper.selectList(Wrappers.<PurchaseApplyItem>lambdaQuery()
                    .eq(PurchaseApplyItem::getApplyId, order.getApplyId())));
            trace.put("inquiries", inquiryMapper.selectList(Wrappers.<Inquiry>lambdaQuery()
                    .eq(Inquiry::getApplyId, order.getApplyId())));
        }
        if (order.getContractId() != null) {
            Contract contract = contractMapper.selectById(order.getContractId());
            if (contract != null && contract.getAwardId() != null) {
                Award award = awardMapper.selectById(contract.getAwardId());
                trace.put("award", award);
                if (award != null) {
                    trace.put("awardItems", awardItemMapper.selectList(Wrappers.<AwardItem>lambdaQuery()
                            .eq(AwardItem::getAwardId, award.getId())));
                }
            }
        }
        List<Arrival> arrivals = arrivalMapper.selectList(Wrappers.<Arrival>lambdaQuery()
                .eq(Arrival::getOrderId, id));
        trace.put("arrivals", arrivals);
        trace.put("changes", orderChangeMapper.selectList(Wrappers.<OrderChange>lambdaQuery()
                .eq(OrderChange::getOrderId, id)));
        return trace;
    }

    // ---------------- 内部方法 ----------------

    /** 校验①：合同 EFFECTIVE 且 valid_from ≤ today ≤ valid_to。 */
    private void checkContractValid(Contract contract) {
        checkContractRow(contract);
        if (contract.getStatus() != ContractStatus.EFFECTIVE) {
            throw new BizException(ResultCode.BIZ_ERROR, "合同未生效：" + contract.getStatus().getDesc());
        }
        LocalDate today = LocalDate.now();
        if (contract.getValidFrom() != null && contract.getValidFrom().isAfter(today)) {
            throw new BizException(ResultCode.BIZ_ERROR, "合同未到生效期");
        }
        if (contract.getValidTo() != null && contract.getValidTo().isBefore(today)) {
            throw new BizException(ResultCode.BIZ_ERROR, "合同已过期");
        }
    }

    private void checkContractRow(Contract contract) {
        if (contract == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "合同不存在");
        }
    }

    private ItemType itemTypeOf(PurchaseApplyItem applyItem) {
        return applyItem.getItemType() == null ? ItemType.MATERIAL : applyItem.getItemType();
    }

    /** 落单（订单头 + 明细快照 + 来源追溯），返回订单 id。 */
    private Long insertOrder(OrderCreateReqVO req, Contract contract, Long supplierId,
                             ItemType type, List<OrderLine> group) {
        PurchaseOrder order = new PurchaseOrder();
        order.setContractId(contract.getId());
        order.setApplyId(req.getApplyId());
        order.setSupplierId(supplierId);
        order.setOrderNo(businessNoGenerator.nextNo("DD"));
        order.setOrderType(type);
        order.setStatus(OrderStatus.CREATED);
        order.setBudgetOccupied(group.stream().map(l -> l.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)); // <!-- D3: P3 改为真实占用 -->
        order.setRemark(req.getRemark());
        save(order);

        for (OrderLine line : group) {
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setSkuId(line.req.getSkuId());
            item.setQtyPurchase(line.req.getQty());
            item.setQtyBase(line.qtyBase);
            JSONObject convSnapshot = new JSONObject();
            convSnapshot.set("purchaseUnit", line.purchaseUnit);
            convSnapshot.set("rate", line.rate);
            item.setConvSnapshot(convSnapshot.toString());
            item.setPrice(line.req.getPrice());
            item.setSourceAwardItem(line.req.getSourceAwardItem());
            item.setApplyItemId(line.req.getApplyItemId());
            item.setPlanDate(line.req.getPlanDate());
            orderItemMapper.insert(item);
        }
        return order.getId();
    }

    /** 下单扣减后回写申请状态：仍有余量 → PARTIAL_ORDER，全部转单 → FULL_ORDER。 */
    private void updateApplyStatusAfterDeduct(Long applyId) {
        PurchaseApply apply = applyMapper.selectById(applyId);
        if (apply == null || (apply.getStatus() != PurchaseApplyStatus.APPROVED
                && apply.getStatus() != PurchaseApplyStatus.PARTIAL_ORDER)) {
            return;
        }
        boolean allOrdered = applyItemMapper.selectList(Wrappers.<PurchaseApplyItem>lambdaQuery()
                        .eq(PurchaseApplyItem::getApplyId, applyId)).stream()
                .allMatch(i -> i.getRemainQty() == null
                        || i.getRemainQty().compareTo(BigDecimal.ZERO) <= 0);
        apply.setStatus(allOrdered ? PurchaseApplyStatus.FULL_ORDER : PurchaseApplyStatus.PARTIAL_ORDER);
        applyMapper.updateById(apply);
    }

    /** 取消回冲后回写申请状态：全部余量恢复 → APPROVED，否则 PARTIAL_ORDER。 */
    private void updateApplyStatusAfterRestore(Long applyId) {
        PurchaseApply apply = applyMapper.selectById(applyId);
        if (apply == null || (apply.getStatus() != PurchaseApplyStatus.FULL_ORDER
                && apply.getStatus() != PurchaseApplyStatus.PARTIAL_ORDER)) {
            return;
        }
        boolean allOrdered = applyItemMapper.selectList(Wrappers.<PurchaseApplyItem>lambdaQuery()
                        .eq(PurchaseApplyItem::getApplyId, applyId)).stream()
                .allMatch(i -> i.getRemainQty() != null
                        && i.getRemainQty().compareTo(BigDecimal.ZERO) <= 0);
        apply.setStatus(allOrdered ? PurchaseApplyStatus.FULL_ORDER : PurchaseApplyStatus.PARTIAL_ORDER);
        applyMapper.updateById(apply);
    }

    /** 下单明细中间结构。 */
    private static class OrderLine {
        OrderCreateReqVO.OrderItemReqVO req;
        PurchaseApplyItem applyItem;
        String purchaseUnit;
        BigDecimal rate;
        BigDecimal qtyBase;
        BigDecimal amount;
    }
}
