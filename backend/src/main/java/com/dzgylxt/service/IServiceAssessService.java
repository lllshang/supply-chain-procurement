package com.dzgylxt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.order.ServiceAssess;
import com.dzgylxt.vo.order.ServiceAssessSaveReqVO;

/** 服务验收考核服务（设计 §2.7：Q7 一期自由录入，P3 结算启动前冻结口径）。 */
public interface IServiceAssessService extends IService<ServiceAssess> {

    /** 仅服务订单（order_type=1）可考核；deduct_amount 供 P3 结算取数 <!-- D8 -->。 */
    Long assess(ServiceAssessSaveReqVO req);
}
