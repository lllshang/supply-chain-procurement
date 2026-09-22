package com.dzgylxt.service.impl.order;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.order.PurchaseOrder;
import com.dzgylxt.entity.order.ServiceAssess;
import com.dzgylxt.enums.AssessStatus;
import com.dzgylxt.enums.ItemType;
import com.dzgylxt.mapper.order.PurchaseOrderMapper;
import com.dzgylxt.mapper.order.ServiceAssessMapper;
import com.dzgylxt.service.IServiceAssessService;
import com.dzgylxt.vo.order.ServiceAssessSaveReqVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** 服务验收考核服务实现（Q7 一期自由录入）。 */
@Service
public class ServiceAssessServiceImpl extends ServiceImpl<ServiceAssessMapper, ServiceAssess>
        implements IServiceAssessService {

    @Autowired
    private PurchaseOrderMapper orderMapper;

    @Override
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
        BigDecimal deduct = req.getDeductAmount() == null ? BigDecimal.ZERO : req.getDeductAmount();
        if (deduct.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "扣款金额不能为负数");
        }
        ServiceAssess assess = new ServiceAssess();
        assess.setOrderId(req.getOrderId());
        assess.setAssessDate(req.getAssessDate());
        assess.setScore(req.getScore());
        assess.setDeductAmount(deduct); // <!-- D8: P3 结算直接取数 -->
        assess.setBasis(req.getBasis());
        assess.setStatus(AssessStatus.NORMAL);
        assess.setRemark(req.getRemark());
        save(assess);
        return assess.getId();
    }
}
