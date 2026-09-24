package com.dzgylxt.service.impl.order;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.BusinessNoGenerator;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.order.Arrival;
import com.dzgylxt.entity.order.ArrivalItem;
import com.dzgylxt.entity.order.OrderItem;
import com.dzgylxt.entity.order.PurchaseOrder;
import com.dzgylxt.enums.AdjustBizType;
import com.dzgylxt.enums.AdjustStatus;
import com.dzgylxt.enums.AdjustType;
import com.dzgylxt.enums.ArrivalStatus;
import com.dzgylxt.enums.DiffType;
import com.dzgylxt.enums.HandleStatus;
import com.dzgylxt.enums.HandleType;
import com.dzgylxt.enums.OrderStatus;
import com.dzgylxt.mapper.order.ArrivalItemMapper;
import com.dzgylxt.mapper.order.ArrivalMapper;
import com.dzgylxt.mapper.order.OrderItemMapper;
import com.dzgylxt.mapper.order.PurchaseOrderMapper;
import com.dzgylxt.service.IArrivalService;
import com.dzgylxt.service.IFulfillmentAdjustService;
import com.dzgylxt.vo.order.AdjustSaveReqVO;
import com.dzgylxt.vo.order.ArrivalCreateReqVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 到货验收服务实现（设计 §2.7；Q8 只做入库台账流水，不做 WMS 库存结存）。
 *
 * <p>状态机：ARRIVAL_CONFIRMED →(部分入库) PARTIAL_STORED →(足额) STORED；
 * 订单回写：首次到货 → PARTIAL_RECEIVED，全部明细足额入库 → RECEIVED。</p>
 */
@Service
public class ArrivalServiceImpl extends ServiceImpl<ArrivalMapper, Arrival> implements IArrivalService {

    @Autowired
    private ArrivalItemMapper arrivalItemMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private PurchaseOrderMapper orderMapper;

    @Autowired
    private BusinessNoGenerator businessNoGenerator;

    @Autowired
    @Lazy
    private IFulfillmentAdjustService adjustService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createArrival(ArrivalCreateReqVO req) {
        if (req.getOrderId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "到货必须关联订单");
        }
        PurchaseOrder order = orderMapper.selectById(req.getOrderId());
        if (order == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "订单不存在：" + req.getOrderId());
        }
        if (order.getStatus() != OrderStatus.CREATED
                && order.getStatus() != OrderStatus.PARTIAL_RECEIVED) {
            throw new BizException(ResultCode.STATUS_INVALID, "当前订单状态不允许到货登记：" + order.getStatus().getDesc());
        }
        List<OrderItem> orderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, req.getOrderId()));
        if (orderItems.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "订单无明细，不能登记到货");
        }
        // 实收覆盖（缺省 = 全额实收未入库余量）
        Map<Long, ArrivalCreateReqVO.ItemActual> actualMap = req.getItems().stream()
                .collect(Collectors.toMap(ArrivalCreateReqVO.ItemActual::getOrderItemId, a -> a));

        Arrival arrival = new Arrival();
        arrival.setOrderId(req.getOrderId());
        arrival.setArrivalNo("DH-" + order.getOrderNo() + "-"
                + businessNoGenerator.nextSubSeq(order.getOrderNo(), 2));
        arrival.setVoucherFileKey(req.getVoucherFileKey());
        arrival.setStatus(ArrivalStatus.ARRIVAL_CONFIRMED);
        arrival.setRemark(req.getRemark());
        save(arrival);

        BigDecimal totalActual = BigDecimal.ZERO;
        BigDecimal totalDiff = BigDecimal.ZERO;
        for (OrderItem orderItem : orderItems) {
            BigDecimal expected = orderItem.getQtyBase() == null ? BigDecimal.ZERO
                    : orderItem.getQtyBase().subtract(storedSum(orderItem.getId()));
            if (expected.compareTo(BigDecimal.ZERO) <= 0) {
                continue; // 已足额入库的明细不再展开
            }
            ArrivalCreateReqVO.ItemActual actual = actualMap.get(orderItem.getId());
            BigDecimal qtyActual = actual == null || actual.getQtyActual() == null
                    ? expected : actual.getQtyActual();
            if (qtyActual.compareTo(BigDecimal.ZERO) < 0 || qtyActual.compareTo(expected) > 0) {
                throw new BizException(ResultCode.PARAM_ERROR,
                        "实收数量非法（0 ≤ 实收 ≤ 应收 " + expected + "）：order_item " + orderItem.getId());
            }
            ArrivalItem item = new ArrivalItem();
            item.setArrivalId(arrival.getId());
            item.setOrderItemId(orderItem.getId());
            item.setSkuId(orderItem.getSkuId());
            item.setQtyExpected(expected);
            item.setQtyActual(qtyActual);
            item.setQtyDiff(expected.subtract(qtyActual));
            item.setDiffType(item.getQtyDiff().compareTo(BigDecimal.ZERO) > 0
                    ? DiffType.SHORTAGE : DiffType.NONE);
            item.setHandleType(HandleType.ACCEPT);
            item.setQtyStored(BigDecimal.ZERO);
            item.setHandleStatus(HandleStatus.PENDING);
            item.setRemark(actual == null ? null : actual.getRemark());
            arrivalItemMapper.insert(item);

            totalActual = totalActual.add(qtyActual);
            totalDiff = totalDiff.add(item.getQtyDiff());
        }
        arrival.setActualQty(totalActual);
        arrival.setDiffQty(totalDiff);
        updateById(arrival);

        // 订单回写：首次到货 → PARTIAL_RECEIVED
        if (order.getStatus() == OrderStatus.CREATED) {
            order.setStatus(OrderStatus.PARTIAL_RECEIVED);
            orderMapper.updateById(order);
        }
        return arrival.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmStore(Long arrivalItemId, BigDecimal qtyStored) {
        if (qtyStored == null || qtyStored.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "入库数量必须大于 0");
        }
        ArrivalItem item = arrivalItemMapper.selectById(arrivalItemId);
        if (item == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "到货明细不存在：" + arrivalItemId);
        }
        BigDecimal stored = item.getQtyStored() == null ? BigDecimal.ZERO : item.getQtyStored();
        if (stored.add(qtyStored).compareTo(item.getQtyActual()) > 0) {
            throw new BizException(ResultCode.PARAM_ERROR,
                    "累计入库超过实收数量：实收 " + item.getQtyActual() + "，已入 " + stored);
        }
        item.setQtyStored(stored.add(qtyStored));
        item.setHandleStatus(item.getQtyStored().compareTo(item.getQtyActual()) >= 0
                ? HandleStatus.DONE : HandleStatus.PENDING);
        arrivalItemMapper.updateById(item);

        // 到货单状态回写
        Arrival arrival = getById(item.getArrivalId());
        List<ArrivalItem> siblings = arrivalItemMapper.selectList(
                new LambdaQueryWrapper<ArrivalItem>().eq(ArrivalItem::getArrivalId, arrival.getId()));
        boolean allStored = siblings.stream().allMatch(i -> i.getQtyStored() != null
                && i.getQtyStored().compareTo(i.getQtyActual()) >= 0);
        boolean anyStored = siblings.stream().anyMatch(i -> i.getQtyStored() != null
                && i.getQtyStored().compareTo(BigDecimal.ZERO) > 0);
        arrival.setStatus(allStored ? ArrivalStatus.STORED
                : (anyStored ? ArrivalStatus.PARTIAL_STORED : arrival.getStatus()));
        updateById(arrival);

        // 订单足额 → RECEIVED
        updateOrderReceived(arrival.getOrderId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleDiff(Long arrivalItemId, HandleType type) {
        ArrivalItem item = arrivalItemMapper.selectById(arrivalItemId);
        if (item == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "到货明细不存在：" + arrivalItemId);
        }
        if (item.getQtyDiff() == null || item.getQtyDiff().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultCode.STATUS_INVALID, "无差异明细无需处理");
        }
        item.setHandleType(type);
        item.setHandleStatus(HandleStatus.DONE);
        arrivalItemMapper.updateById(item);

        // 退货/补货 → 联动登记履约调整草稿（留痕跟踪项，设计 §2.7）
        if (type == HandleType.RETURN || type == HandleType.REPLENISH) {
            Arrival arrival = getById(item.getArrivalId());
            AdjustSaveReqVO req = new AdjustSaveReqVO();
            req.setBizType(AdjustBizType.ARRIVAL);
            req.setOrderId(arrival.getOrderId());
            req.setArrivalId(arrival.getId());
            req.setAdjustType(type == HandleType.RETURN ? AdjustType.RETURN : AdjustType.REPLENISH);
            req.setReason((type == HandleType.RETURN ? "到货差异退货" : "到货差异补货")
                    + "：明细 " + item.getId() + "，差异 " + item.getQtyDiff());
            req.setAmount(BigDecimal.ZERO); // 差异台账调整，金额差额由变更单据口径补充
            req.setBeforeJson(JSONUtil.toJsonStr(Map.of("qtyDiff", item.getQtyDiff())));
            req.setAfterJson(JSONUtil.toJsonStr(Map.of("handleType", type.getDesc())));
            adjustService.createAdjust(req);
        }
    }

    /**
     * P3c-A4：整单拒收退货（PRD L827 状态机"待验收入库→拒收退货：全部拒收"；
     * L809"整单拒收：全部合格量置 0，差异行选择退货，必须填写原因并上传凭证"）。
     *
     * <p>守卫：已全部入库（STORED）不可整拒（3003）；已拒收单据幂等拒绝；
     * 原因与凭证必填（L809 强制）。执行后订单到货缺口回到可收状态（合格量归零即可再登记）。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectAll(Long arrivalId, String reason, String voucherFileKey) {
        Arrival arrival = getById(arrivalId);
        if (arrival == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "到货单不存在：" + arrivalId);
        }
        if (arrival.getStatus() == ArrivalStatus.REJECTED_RETURNED) {
            throw new BizException(ResultCode.STATUS_INVALID, "该到货单已拒收退货，请勿重复操作");
        }
        if (arrival.getStatus() == ArrivalStatus.STORED) {
            throw new BizException(ResultCode.STATUS_INVALID,
                    "已全部入库的到货单不可整单拒收（如需退回应走退货流程）");
        }
        if (reason == null || reason.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "整单拒收必须填写原因");
        }
        if (voucherFileKey == null || voucherFileKey.isBlank()) {
            throw new BizException(ResultCode.PARAM_ERROR, "整单拒收必须上传凭证");
        }

        List<ArrivalItem> items = listItems(arrivalId);
        for (ArrivalItem item : items) {
            // 全部合格量置 0（L809）
            item.setQtyStored(BigDecimal.ZERO);
            // 差异行统一标记退货
            if (item.getQtyDiff() != null && item.getQtyDiff().compareTo(BigDecimal.ZERO) != 0) {
                item.setHandleType(HandleType.RETURN);
                item.setHandleStatus(HandleStatus.DONE);
            }
            arrivalItemMapper.updateById(item);
        }

        arrival.setStatus(ArrivalStatus.REJECTED_RETURNED);
        arrival.setRemark(reason);
        arrival.setVoucherFileKey(voucherFileKey);
        arrival.setActualQty(BigDecimal.ZERO);
        updateById(arrival);
    }

    @Override
    public List<ArrivalItem> listItems(Long arrivalId) {
        return arrivalItemMapper.selectList(new LambdaQueryWrapper<ArrivalItem>()
                .eq(ArrivalItem::getArrivalId, arrivalId).orderByAsc(ArrivalItem::getId));
    }

    @Override
    public IPage<ArrivalItem> ledger(long current, long size, Long orderId, Long supplierId,
                                     java.time.LocalDate from, java.time.LocalDate to) {
        // 台账流水：arrival_item + arrival（按订单/供应商/日期筛选）
        List<Long> arrivalIds = null;
        if (orderId != null || supplierId != null) {
            LambdaQueryWrapper<Arrival> wrapper = new LambdaQueryWrapper<>();
            if (orderId != null) {
                wrapper.eq(Arrival::getOrderId, orderId);
            }
            if (supplierId != null) {
                List<PurchaseOrder> orders = orderMapper.selectList(
                        new LambdaQueryWrapper<PurchaseOrder>().eq(PurchaseOrder::getSupplierId, supplierId));
                Set<Long> ids = orders.stream().map(PurchaseOrder::getId).collect(Collectors.toSet());
                if (ids.isEmpty()) {
                    return new Page<>(current, size);
                }
                wrapper.in(Arrival::getOrderId, ids);
            }
            List<Arrival> arrivals = list(wrapper);
            arrivalIds = arrivals.stream().map(Arrival::getId).toList();
            if (arrivalIds.isEmpty()) {
                return new Page<>(current, size);
            }
        }
        LambdaQueryWrapper<ArrivalItem> itemWrapper = new LambdaQueryWrapper<>();
        if (arrivalIds != null) {
            itemWrapper.in(ArrivalItem::getArrivalId, arrivalIds);
        }
        if (from != null) {
            itemWrapper.ge(ArrivalItem::getCreatedAt, from.atStartOfDay());
        }
        if (to != null) {
            itemWrapper.lt(ArrivalItem::getCreatedAt, to.plusDays(1).atStartOfDay());
        }
        return arrivalItemMapper.selectPage(new Page<>(current, size),
                itemWrapper.orderByDesc(ArrivalItem::getId));
    }

    /** 明细的历史已入库合计（跨到货单累加）。 */
    private BigDecimal storedSum(Long orderItemId) {
        List<ArrivalItem> prior = arrivalItemMapper.selectList(
                new LambdaQueryWrapper<ArrivalItem>().eq(ArrivalItem::getOrderItemId, orderItemId));
        return prior.stream().map(i -> i.getQtyStored() == null ? BigDecimal.ZERO : i.getQtyStored())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 全部明细足额入库 → 订单 RECEIVED。 */
    private void updateOrderReceived(Long orderId) {
        PurchaseOrder order = orderMapper.selectById(orderId);
        if (order == null || order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }
        List<OrderItem> orderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        Set<Long> orderItemIds = orderItems.stream()
                .map(OrderItem::getId).collect(Collectors.toCollection(HashSet::new));
        boolean allStored = !orderItemIds.isEmpty();
        for (Long orderItemId : orderItemIds) {
            BigDecimal qtyBase = orderItems.stream()
                    .filter(i -> i.getId().equals(orderItemId)).findFirst()
                    .map(OrderItem::getQtyBase).orElse(BigDecimal.ZERO);
            if (storedSum(orderItemId).compareTo(qtyBase) < 0) {
                allStored = false;
                break;
            }
        }
        if (allStored) {
            order.setStatus(OrderStatus.RECEIVED);
            orderMapper.updateById(order);
        }
    }
}
