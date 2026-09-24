package com.dzgylxt.service.impl.order;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.order.PurchaseOrder;
import com.dzgylxt.entity.order.ServiceAssess;
import com.dzgylxt.entity.order.ServiceDeductionItem;
import com.dzgylxt.enums.AssessStatus;
import com.dzgylxt.enums.ItemType;
import com.dzgylxt.mapper.order.OrderItemMapper;
import com.dzgylxt.mapper.order.PurchaseOrderMapper;
import com.dzgylxt.mapper.order.ServiceAssessMapper;
import com.dzgylxt.mapper.order.ServiceDeductionItemMapper;
import com.dzgylxt.service.IServiceAssessService;
import com.dzgylxt.vo.order.ServiceAssessSaveReqVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/** 服务验收考核服务实现（Q7 一期自由录入）。 */
@Service
public class ServiceAssessServiceImpl extends ServiceImpl<ServiceAssessMapper, ServiceAssess>
        implements IServiceAssessService {

    @Autowired
    private PurchaseOrderMapper orderMapper;

    /** P3c-A5：扣款明细子表（Σ 明细回填 service_assess.deduct_amount）。 */
    @Autowired
    private ServiceDeductionItemMapper deductionItemMapper;

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Long assess(ServiceAssessSaveReqVO req) {
        if (req.getOrderId() == null || req.getAssessDate() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "服务订单与考核日期必填");
        }
        PurchaseOrder order = orderMapper.selectById(req.getOrderId());
        if (order == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "订单不存在：" + req.getOrderId());
        }
        // 仅服务订单可考核（order_type=1）
        if (order.getOrderType() != ItemType.SERVICE) {
            throw new BizException(ResultCode.STATUS_INVALID, "仅服务订单可进行服务考核");
        }
        // P3c-A5：有明细时以 Σ 明细为扣款额（L812 一条或多条）；否则沿用单值（旧接口兼容）
        List<ServiceAssessSaveReqVO.DeductionItemVO> items = req.getDeductionItems();
        boolean hasItems = items != null && !items.isEmpty();
        BigDecimal deduct;
        if (hasItems) {
            deduct = BigDecimal.ZERO;
            for (ServiceAssessSaveReqVO.DeductionItemVO vo : items) {
                if (vo.getItemName() == null || vo.getItemName().isBlank()) {
                    throw new BizException(ResultCode.PARAM_ERROR, "扣款明细必须填写扣款项目（考核指标）");
                }
                if (vo.getAmount() == null || vo.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BizException(ResultCode.PARAM_ERROR, "扣款明细金额必须大于 0");
                }
                deduct = deduct.add(vo.getAmount());
            }
            deduct = deduct.setScale(2, java.math.RoundingMode.HALF_UP);
        } else {
            deduct = req.getDeductAmount() == null ? BigDecimal.ZERO : req.getDeductAmount();
        }
        if (deduct.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "扣款金额不能为负数");
        }
        // 应付非负守卫（L812"扣款金额不得导致应付为负"）：Σ扣款 ≤ 订单应结总额
        if (deduct.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal payableBase = payableBaseOf(req.getOrderId());
            if (deduct.compareTo(payableBase) > 0) {
                throw new BizException(ResultCode.BIZ_ERROR,
                        "扣款合计 " + deduct + " 超出订单应付基数 " + payableBase + "（扣款不得导致应付为负）");
            }
        }

        ServiceAssess assess = new ServiceAssess();
        assess.setOrderId(req.getOrderId());
        assess.setAssessDate(req.getAssessDate());
        assess.setScore(req.getScore());
        assess.setDeductAmount(deduct); // <!-- D8: P3 结算直接取数 -->（= Σ 明细，汇总冗余）
        assess.setBasis(req.getBasis());
        assess.setStatus(AssessStatus.NORMAL);
        assess.setRemark(req.getRemark());
        save(assess);

        // 明细落子表（同事务；既有单条 deduct 场景不写明细，读取端兼容）
        if (hasItems) {
            for (ServiceAssessSaveReqVO.DeductionItemVO vo : items) {
                ServiceDeductionItem row = new ServiceDeductionItem();
                row.setAssessId(assess.getId());
                row.setItemName(vo.getItemName());
                row.setAmount(vo.getAmount().setScale(2, java.math.RoundingMode.HALF_UP));
                row.setReason(vo.getReason());
                deductionItemMapper.insert(row);
            }
        }
        return assess.getId();
    }

    /**
     * P3c-A5：应付基数 = 订单金额 − 历史已扣款合计（同一订单多次考核时按剩余应付校验，
     * 防重复考核叠加导致应付为负）。订单金额取 Σ 明细 price × qty_base。
     */
    private BigDecimal payableBaseOf(Long orderId) {
        BigDecimal orderAmount = orderItemMapper == null ? BigDecimal.ZERO
                : orderItemMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query
                        .LambdaQueryWrapper<com.dzgylxt.entity.order.OrderItem>()
                        .eq(com.dzgylxt.entity.order.OrderItem::getOrderId, orderId)).stream()
                .map(i -> (i.getPrice() == null ? BigDecimal.ZERO : i.getPrice())
                        .multiply(i.getQtyBase() == null ? BigDecimal.ZERO : i.getQtyBase()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal deducted = baseMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query
                                .LambdaQueryWrapper<ServiceAssess>()
                                .eq(ServiceAssess::getOrderId, orderId)
                                .eq(ServiceAssess::getStatus, AssessStatus.NORMAL)).stream()
                .map(a -> a.getDeductAmount() == null ? BigDecimal.ZERO : a.getDeductAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return orderAmount.subtract(deducted).max(BigDecimal.ZERO);
    }

    @Autowired
    private OrderItemMapper orderItemMapper;
}
