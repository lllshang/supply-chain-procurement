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
import com.dzgylxt.service.IBudgetOccupyService;
import com.dzgylxt.service.IPriceHistoryService;
import com.dzgylxt.enums.BudgetBizType;
import com.dzgylxt.service.IOrderService;
import com.dzgylxt.vo.budget.BudgetOccupyCmd;
import com.dzgylxt.vo.budget.BudgetTransferCmd;
import com.dzgylxt.vo.budget.OccupyResultVO;
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

    @Autowired
    private IPriceHistoryService priceHistoryService;

    @Autowired
    private IBudgetOccupyService budgetOccupyService;

    /** #47：预算拦截升级审批网关（可选依赖，单测可缺省）。 */
    @Autowired(required = false)
    private ApprovalGateway approvalGateway;

    /** #47：REQUIRES_NEW 落审批任务用事务管理器（拦截回滚后任务仍需留存）。 */
    @Autowired(required = false)
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    /** #47：预算升级任务放行标记消费查询。 */
    @Autowired(required = false)
    private com.dzgylxt.mapper.approval.ApprovalTaskMapper approvalTaskMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> createOrder(OrderCreateReqVO req) {
        // P2b/L747：日常订单可不关联申请（applyId 可空）——预算占用锚点=award/申请，合同额度闸必须通过
        if (req.getContractId() == null || req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "合同/明细均必填");
        }
        for (OrderCreateReqVO.OrderItemReqVO item : req.getItems()) {
            // P2b：有申请明细则 applyItemId 必填；无申请来源明细（applyItemId=null）必须自带 itemType
            if (item.getSkuId() == null
                    || item.getQty() == null || item.getQty().compareTo(BigDecimal.ZERO) <= 0
                    || item.getPrice() == null || item.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException(ResultCode.PARAM_ERROR, "下单明细行的申请明细/SKU/数量/单价非法");
            }
        }

        // #48 申请头状态闸门：作废（CLOSED）/驳回等非有效状态申请不可下单
        // （仅 APPROVED / PARTIAL_ORDER 为可下单有效态；FULL_ORDER 无余量、DRAFT/BUDGET_PENDING/
        //   PURCHASE_PENDING 未审结、REJECTED/CLOSED 非有效——统一拦截）
        // P2b/L747 分流：日常订单可不关联申请（applyId=null，预算锚点=award/合同），
        // 此时跳过申请头闸门与余量闸，仅保留合同额度闸（校验②）+价格校验。
        PurchaseApply applyHead = null;
        if (req.getApplyId() != null) {
            applyHead = applyMapper.selectById(req.getApplyId());
            if (applyHead == null) {
                throw new BizException(ResultCode.DATA_NOT_FOUND, "采购申请不存在：" + req.getApplyId());
            }
            if (applyHead.getStatus() != PurchaseApplyStatus.APPROVED
                    && applyHead.getStatus() != PurchaseApplyStatus.PARTIAL_ORDER) {
                throw new BizException(ResultCode.STATUS_INVALID, "申请状态不允许下单："
                        + applyHead.getStatus().getDesc() + "（仅已审批/部分转单可下单）");
            }
        }

        // ① Redis 锁（顺序固定 contract → apply；获取失败快速失败）
        String tokenC = redisLockUtil.tryLock("contract:" + req.getContractId(), LOCK_WAIT_MILLIS);
        if (tokenC == null) {
            throw new BizException(ResultCode.BIZ_ERROR, "订单提交繁忙，请稍后重试");
        }
        String tokenA = null;
        if (req.getApplyId() != null) {
            tokenA = redisLockUtil.tryLock("apply:" + req.getApplyId(), LOCK_WAIT_MILLIS);
            if (tokenA == null) {
                redisLockUtil.unlock("contract:" + req.getContractId(), tokenC);
                throw new BizException(ResultCode.BIZ_ERROR, "订单提交繁忙，请稍后重试");
            }
        }
        try {
            // ② contract 行锁（第一把行锁）
            Contract contract = contractMapper.selectForUpdate(req.getContractId());
            // 校验①：合同 EFFECTIVE 且在有效期
            checkContractValid(contract);

            Long supplierId = req.getSupplierId() == null ? contract.getSupplierId() : req.getSupplierId();

            // ③ apply_item 行锁（调用方排序去重，配合主键 IN 扫描保证锁序一致，杜绝交叉死锁）
            // P2b/L747：无申请来源订单（明细不挂 applyItemId）跳过本段，仅走合同额度闸（校验②）
            Map<Long, PurchaseApplyItem> itemMap = new LinkedHashMap<>();
            List<Long> itemIds = req.getItems().stream()
                    .map(OrderCreateReqVO.OrderItemReqVO::getApplyItemId)
                    .filter(java.util.Objects::nonNull)
                    .distinct().sorted().toList();
            boolean hasApplyItems = !itemIds.isEmpty();
            if (hasApplyItems) {
                List<PurchaseApplyItem> lockedItems = applyItemMapper.selectForUpdateByIds(itemIds);
                for (PurchaseApplyItem locked : lockedItems) {
                    itemMap.put(locked.getId(), locked);
                }
            }
            for (OrderCreateReqVO.OrderItemReqVO item : req.getItems()) {
                if (item.getApplyItemId() != null && !itemMap.containsKey(item.getApplyItemId())) {
                    throw new BizException(ResultCode.PARAM_ERROR, "申请明细不存在：" + item.getApplyItemId());
                }
                if (item.getApplyItemId() == null && (item.getPurchaseUnit() == null || item.getPurchaseUnit().isBlank())) {
                    throw new BizException(ResultCode.PARAM_ERROR,
                            "无申请来源明细必须指定采购单位（无申请明细可取默认单位）：SKU " + item.getSkuId());
                }
            }

            // 逐明细：锁内取换算快照 + 余量校验与条件扣减（version 兜底，失败重试 1 次）
            List<OrderLine> lines = new ArrayList<>();
            for (OrderCreateReqVO.OrderItemReqVO item : req.getItems()) {
                PurchaseApplyItem applyItem = item.getApplyItemId() == null ? null : itemMap.get(item.getApplyItemId());
                LocalDateTime now = LocalDateTime.now();
                String unit = item.getPurchaseUnit() == null || item.getPurchaseUnit().isBlank()
                        ? (applyItem == null ? null : applyItem.getPurchaseUnit()) : item.getPurchaseUnit();
                UnitConversion conv = unit == null || unit.isBlank() ? null
                        : unitConversionMapper.selectCurrentEffective(item.getSkuId(), unit, now);
                BigDecimal rate = conv == null || conv.getRate() == null ? BigDecimal.ONE : conv.getRate();
                BigDecimal qtyBase = item.getQty().multiply(rate);

                if (applyItem != null) {
                    // 校验③：ordered_qty + 本单数量 ≤ apply_qty（P2b：无申请来源明细跳过，走合同额度闸）
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
                }

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
            // P2b：D2 线下补录存量合同可能缺 version/available_amount——空值兜底
            //（version 空=0 首版；available_amount 空=全额可用），避免 NPE 并保证预算闸可达
            Integer contractVersion = contract.getVersion() == null ? Integer.valueOf(0) : contract.getVersion();
            int deducted = contractMapper.deductAvailable(contract.getId(), totalAmount, contractVersion);
            if (deducted == 0) {
                Contract fresh = contractMapper.selectById(contract.getId());
                Integer freshVersion = fresh.getVersion() == null ? Integer.valueOf(0) : fresh.getVersion();
                deducted = contractMapper.deductAvailable(contract.getId(), totalAmount, freshVersion);
            }
            if (deducted == 0) {
                throw new BizException(ResultCode.BIZ_ERROR,
                        "超出合同可用额度：可用 " + availableAmountOf(contract) + "，本单 " + totalAmount);
            }
            contract.setAvailableAmount(availableAmountOf(contract).subtract(totalAmount));

            // 落单：物料/服务按 item_type 拆单（设计 §2.6）
            List<Long> orderIds = new ArrayList<>();
            for (ItemType type : new ItemType[]{ItemType.MATERIAL, ItemType.SERVICE}) {
                List<OrderLine> group = lines.stream()
                        .filter(l -> itemTypeOf(l.req, l.applyItem) == type).toList();
                if (group.isEmpty()) {
                    continue;
                }
                orderIds.add(insertOrder(req, contract, supplierId, type, group));
            }
            contractMapper.updateById(contract);

            // 申请状态回写：APPROVED → PARTIAL_ORDER / FULL_ORDER（P2b：无申请来源订单跳过）
            if (req.getApplyId() != null) {
                updateApplyStatusAfterDeduct(req.getApplyId());
            }
            return orderIds;
        } finally {
            // 逆序释放 Redis 锁（P2b：无申请来源订单未加 apply 锁）
            if (tokenA != null) {
                redisLockUtil.unlock("apply:" + req.getApplyId(), tokenA);
            }
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
            // P3 §3 行5：取消释放订单占用（按日志余额，守恒回冲）
            BigDecimal occupied = budgetOccupyService.occupiedTotal(BudgetBizType.ORDER, id);
            if (occupied != null && occupied.compareTo(BigDecimal.ZERO) > 0) {
                BudgetOccupyCmd releaseCmd = new BudgetOccupyCmd();
                releaseCmd.setAmount(occupied);
                releaseCmd.setBizType(BudgetBizType.ORDER);
                releaseCmd.setBizId(id);
                releaseCmd.setRemark("订单取消释放-" + order.getOrderNo());
                budgetOccupyService.release(releaseCmd);
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
            Integer contractVersion = contract.getVersion() == null ? Integer.valueOf(0) : contract.getVersion();
            if (amountDelta.compareTo(BigDecimal.ZERO) > 0) {
                int updated = contractMapper.deductAvailable(contract.getId(), amountDelta, contractVersion);
                if (updated == 0) {
                    throw new BizException(ResultCode.BIZ_ERROR, "变更超出合同可用额度");
                }
            } else if (amountDelta.compareTo(BigDecimal.ZERO) < 0) {
                contractMapper.releaseAvailable(contract.getId(), amountDelta.abs(), contractVersion);
            }
            // P3 §3 行7/行8（#47 修订 + P2b-10 根因修复）：变更对冲——
            // <b>预算锚点解析提到 occupied>0 门之外</b>（原实现把拦截块包在占用门内：
            // 无锚订单 occupied=0 进不了拦截块（静默绕过）；有锚订单锚链必命中，
            // occupyCmd==null 分支不可达——双重死代码）。现：变更增额<b>必解析锚点</b>，
            // 解析失败 → 4000 硬控拦截（不静默、不跳过）；增量被预算拦截 → 拦截 +
            // 生成 BUDGET 升级审批任务，升级通过后 force 占用生效，重提变更时消费放行标记
            BigDecimal currentOccupied = order.getBudgetOccupied() == null
                    ? BigDecimal.ZERO : order.getBudgetOccupied();
            BigDecimal newOccupied = currentOccupied.add(amountDelta);
            if (amountDelta.compareTo(BigDecimal.ZERO) > 0) {
                // #47 放行标记：存在已审批未消费的变更升级任务 → 消费并跳过占用
                //（升级通过时已 force 预挂占用，正常占用会重复计账）
                boolean upgradeCovered = consumeApprovedBudgetUpgrade(order, amountDelta);
                if (!upgradeCovered) {
                    // P2b-5/10：变更预算锚点回填链（申请 → contract.award_id → award.dept/subject，
                    // 科目缺省回落 contract.subject_id）——增额必过预算硬控
                    BudgetOccupyCmd occupyCmd = buildChangeOccupyCmd(order, amountDelta);
                    if (occupyCmd == null) {
                        // 锚点缺失 = 无法校验 = 硬控拦截（禁止静默绕过；PRD §6.4.3 / P3 #47 意图）
                        throw new BizException(ResultCode.BIZ_ERROR,
                                "变更增额无预算锚点（无申请来源且合同未关联有效定标），预算硬控拦截："
                                        + "请先补录预算锚点或改走线下补录通道");
                    }
                    OccupyResultVO result = budgetOccupyService.occupy(occupyCmd);
                    if (!result.isAvailable()) {
                        // #47：拦截 + 升级——生成 BUDGET 升级审批任务后回滚本次变更
                        createBudgetUpgradeTask(order, amountDelta, occupyCmd, result);
                        throw new BizException(ResultCode.BIZ_ERROR,
                                "变更增额超出预算余额：" + result.getMessage()
                                        + "；已生成 BUDGET 升级审批，通过后请重提变更");
                    }
                } else {
                    // 升级占用已预挂（含 budgetOccupied 增量），重提不再重复累加
                    newOccupied = currentOccupied;
                }
            } else if (amountDelta.compareTo(BigDecimal.ZERO) < 0) {
                BudgetOccupyCmd releaseCmd = new BudgetOccupyCmd();
                releaseCmd.setAmount(amountDelta.negate());
                releaseCmd.setBizType(BudgetBizType.ORDER);
                releaseCmd.setBizId(id);
                releaseCmd.setRemark("订单变更减额释放");
                budgetOccupyService.release(releaseCmd);
            }
            order.setBudgetOccupied(newOccupied);
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

    /**
     * P2b-5：变更增额预算锚点回填链——① order.applyId → apply.dept/subject/expectedDate；
     * ② contract.award_id → award.dept/subject（D9 线下定标锚点，expectedDate 空 = 提交当月）。
     * 两条链均未命中返回 null（调用方硬控拦截，禁止静默绕过）。
     */
    private BudgetOccupyCmd buildChangeOccupyCmd(PurchaseOrder order, BigDecimal amountDelta) {
        BudgetOccupyCmd cmd = new BudgetOccupyCmd();
        if (order.getApplyId() != null) {
            PurchaseApply apply = applyMapper.selectById(order.getApplyId());
            if (apply != null && apply.getDeptId() != null) {
                cmd.setDeptId(apply.getDeptId());
                // 控制单元=部门×科目×月份（QA2-01 口径）
                cmd.setSubjectId(apply.getBudgetSubjectId());
                cmd.setExpectedDate(apply.getExpectedDate());
            }
        }
        if (cmd.getDeptId() == null && order.getContractId() != null) {
            Contract contract = contractMapper.selectById(order.getContractId());
            if (contract != null && contract.getAwardId() != null) {
                com.dzgylxt.entity.purchase.Award award = awardMapper.selectById(contract.getAwardId());
                if (award != null && award.getDeptId() != null) {
                    cmd.setDeptId(award.getDeptId());
                    // P2b-10：科目锚点二源——award.subject_id 缺省回落 contract.subject_id
                    cmd.setSubjectId(award.getSubjectId() != null
                            ? award.getSubjectId() : contract.getSubjectId());
                }
            }
        }
        if (cmd.getDeptId() == null) {
            return null;
        }
        cmd.setAmount(amountDelta);
        cmd.setBizType(BudgetBizType.ORDER);
        cmd.setBizId(order.getId());
        cmd.setRemark("订单变更增额");
        return cmd;
    }

    /**
     * #47：变更增额被预算拦截时生成 BUDGET 升级审批任务。
     *
     * <p>任务必须在本事务回滚后留存（拦截即回滚）——经 REQUIRES_NEW 独立事务落库；
     * payload 标记 orderChange=true，升级通过后由 BudgetApprovalHandler force 占用生效。</p>
     */
    private void createBudgetUpgradeTask(PurchaseOrder order, BigDecimal amountDelta,
                                         BudgetOccupyCmd occupyCmd, OccupyResultVO blocked) {
        if (approvalGateway == null) {
            return;
        }
        cn.hutool.json.JSONObject payload = new cn.hutool.json.JSONObject();
        payload.set("orderChange", true);
        payload.set("orderId", order.getId());
        payload.set("applyId", order.getApplyId());
        payload.set("deptId", occupyCmd.getDeptId());
        // P2b-5：升级 force 占用同样落到锚点科目行（控制单元=部门×科目×月份）
        payload.set("subjectId", occupyCmd.getSubjectId());
        payload.set("expectedDate", occupyCmd.getExpectedDate() == null
                ? null : occupyCmd.getExpectedDate().toString());
        payload.set("amount", amountDelta);
        payload.set("overAmount", blocked.getOverAmount());
        payload.set("balance", blocked.getBalance());
        payload.set("budgetStatus", 2);
        com.dzgylxt.approval.ApprovalTaskSpec spec = new com.dzgylxt.approval.ApprovalTaskSpec();
        spec.setBizType("BUDGET");
        // QA2-06：bizId 固定为订单 id（bizType=BUDGET + orderId 定位单次变更升级），
        // 防同一申请多次变更时按 applyId 串单（放行标记按 金额+订单 精确消费）
        spec.setBizId(order.getId());
        spec.setTitle("预算升级-订单变更" + order.getOrderNo());
        spec.setPayloadJson(payload.toString());
        if (transactionManager != null) {
            // REQUIRES_NEW：拦截变更回滚后任务留存
            org.springframework.transaction.support.TransactionTemplate template =
                    new org.springframework.transaction.support.TransactionTemplate(transactionManager);
            template.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            template.executeWithoutResult(status -> approvalGateway.create(spec));
        } else {
            approvalGateway.create(spec);
        }
    }

    /**
     * #47：消费"已审批未消费"的订单变更升级任务（放行标记）。
     *
     * <p>升级通过时差额已 force 占用（budgetOccupied 已累加），重提变更据此跳过正常
     * 占用与增量累加，避免双计；按 金额+订单 精确匹配且一次性消费（consumed 标记）。</p>
     *
     * @return true=存在匹配任务并已消费（调用方跳过占用）
     */
    private boolean consumeApprovedBudgetUpgrade(PurchaseOrder order, BigDecimal amountDelta) {
        // P2b-5：无申请来源订单（D9 锚点）同样可走升级放行——bizId=订单 id（QA2-06）已可精确定位，
        // 不再以 applyId 为前置门（原条件导致线下订单升级任务永不消费）
        if (approvalTaskMapper == null) {
            return false;
        }
        List<com.dzgylxt.entity.approval.ApprovalTask> tasks = approvalTaskMapper.selectList(
                Wrappers.<com.dzgylxt.entity.approval.ApprovalTask>lambdaQuery()
                        .eq(com.dzgylxt.entity.approval.ApprovalTask::getBizType, "BUDGET")
                        // QA2-06：bizId=订单 id（与 createBudgetUpgradeTask 对齐）
                        .eq(com.dzgylxt.entity.approval.ApprovalTask::getBizId, order.getId())
                        .eq(com.dzgylxt.entity.approval.ApprovalTask::getStatus,
                                com.dzgylxt.enums.ApprovalStatus.APPROVED)
                        .orderByDesc(com.dzgylxt.entity.approval.ApprovalTask::getId));
        for (com.dzgylxt.entity.approval.ApprovalTask task : tasks) {
            if (task.getPayloadJson() == null || task.getPayloadJson().isBlank()) {
                continue;
            }
            JSONObject payload = JSONUtil.parseObj(task.getPayloadJson());
            if (!payload.getBool("orderChange", false)
                    || !order.getId().equals(payload.getLong("orderId"))
                    || payload.getBool("consumed", false)) {
                continue;
            }
            BigDecimal amount = payload.getBigDecimal("amount");
            if (amount == null || amount.compareTo(amountDelta) != 0) {
                continue;
            }
            payload.set("consumed", true);
            task.setPayloadJson(payload.toString());
            approvalTaskMapper.updateById(task);
            return true;
        }
        return false;
    }

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

    /** 合同可用额度空值兜底（D2 线下补录存量合同缺省=全额可用 amount）。 */
    private BigDecimal availableAmountOf(Contract contract) {
        return contract.getAvailableAmount() == null
                ? (contract.getAmount() == null ? BigDecimal.ZERO : contract.getAmount())
                : contract.getAvailableAmount();
    }

    private ItemType itemTypeOf(PurchaseApplyItem applyItem) {
        return applyItem.getItemType() == null ? ItemType.MATERIAL : applyItem.getItemType();
    }

    /** 行类型：有申请明细取申请行类型；无申请来源明细（P2b/L747）取 req.itemType，缺省 MATERIAL。 */
    private ItemType itemTypeOf(OrderCreateReqVO.OrderItemReqVO reqItem, PurchaseApplyItem applyItem) {
        if (applyItem != null) {
            return itemTypeOf(applyItem);
        }
        return reqItem.getItemType() == null ? ItemType.MATERIAL : reqItem.getItemType();
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
        // P3：真实占用（转移自申请）；P2b-6：无预算锚点订单（无申请且合同未关联定标）
        // budget_occupied 置 0——不得记与预算系统脱钩的假占用（锚点补录走 D2 线下通道）
        boolean hasAnchor = req.getApplyId() != null || contract.getAwardId() != null;
        order.setBudgetOccupied(hasAnchor
                ? group.stream().map(l -> l.amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                : BigDecimal.ZERO);
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
            // 价格库埋点③：订单生成（P3 §1.4；价格取基本单位口径 #27）
            priceHistoryService.record(item.getSkuId(), order.getSupplierId(), item.getPrice(),
                    com.dzgylxt.enums.PriceSource.ORDER, "ORDER", order.getId(),
                    "订单生成-" + order.getOrderNo());
        }

        // P3 §3 行6：占用主体转移（申请→订单，金额不变；不重复计 used_amount）
        if (req.getApplyId() != null && order.getBudgetOccupied().compareTo(BigDecimal.ZERO) > 0) {
            BudgetTransferCmd transferCmd = new BudgetTransferCmd();
            transferCmd.setFromBizType(BudgetBizType.APPLY);
            transferCmd.setFromBizId(req.getApplyId());
            transferCmd.setToBizType(BudgetBizType.ORDER);
            transferCmd.setToBizId(order.getId());
            transferCmd.setAmount(order.getBudgetOccupied());
            transferCmd.setRemark("下单转移-订单" + order.getOrderNo());
            budgetOccupyService.transfer(transferCmd);
        } else if (req.getApplyId() == null
                && contract.getAwardId() != null
                && order.getBudgetOccupied().compareTo(BigDecimal.ZERO) > 0) {
            // P2b-6：无申请来源订单（D9 线下定标锚点）→ AWARD→ORDER 同行转移落 ORDER 流水，
            // budget_occupied 与预算系统挂钩（取消/变更释放按 occupiedTotal(ORDER) 守恒回冲）；
            // award 无占用流水时 transfer 显式告警跳过（D2 手工合同无锚点场景，不阻断下单）
            BudgetTransferCmd transferCmd = new BudgetTransferCmd();
            transferCmd.setFromBizType(BudgetBizType.AWARD);
            transferCmd.setFromBizId(contract.getAwardId());
            transferCmd.setToBizType(BudgetBizType.ORDER);
            transferCmd.setToBizId(order.getId());
            transferCmd.setAmount(order.getBudgetOccupied());
            transferCmd.setRemark("线下定标下单转移-订单" + order.getOrderNo());
            budgetOccupyService.transfer(transferCmd);
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

        // P3 §3 行8：FULL_ORDER 释放申请占用余量（Σ申请占用 − Σ订单承接额）
        if (allOrdered) {
            BigDecimal applyOccupied = budgetOccupyService.occupiedTotal(BudgetBizType.APPLY, applyId);
            BigDecimal transferred = list(Wrappers.<PurchaseOrder>lambdaQuery()
                            .eq(PurchaseOrder::getApplyId, applyId)).stream()
                    .map(o -> o.getBudgetOccupied() == null ? BigDecimal.ZERO : o.getBudgetOccupied())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal remainder = applyOccupied.subtract(transferred);
            if (remainder.compareTo(BigDecimal.ZERO) > 0) {
                BudgetOccupyCmd releaseCmd = new BudgetOccupyCmd();
                releaseCmd.setAmount(remainder);
                releaseCmd.setBizType(BudgetBizType.APPLY);
                releaseCmd.setBizId(applyId);
                releaseCmd.setRemark("全额转单释放申请占用余量");
                budgetOccupyService.release(releaseCmd);
            }
        }
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
