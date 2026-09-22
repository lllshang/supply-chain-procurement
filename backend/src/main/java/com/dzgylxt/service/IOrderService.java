package com.dzgylxt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.order.OrderItem;
import com.dzgylxt.entity.order.PurchaseOrder;
import com.dzgylxt.vo.order.OrderChangeReqVO;
import com.dzgylxt.vo.order.OrderCreateReqVO;

import java.util.List;

/** 采购订单服务（设计 §2.6 / §4：三重校验事务为核心硬规则）。 */
public interface IOrderService extends IService<PurchaseOrder> {

    /**
     * 下单三重校验事务：Redis 锁(contract/apply，固定锁序) → contract 行锁 FOR UPDATE
     * → apply_item 行锁 → 扣余量/扣额度（version 乐观兜底）→ 落快照/落单；
     * 物料/服务按 item_type 拆单；budget_occupied=本单金额快照（<!-- D3 -->）。
     */
    List<Long> createOrder(OrderCreateReqVO req);

    /** 仅 CREATED/PARTIAL_RECEIVED；释放合同额度；order_change(change_type=4)。 */
    void cancelOrder(Long id, String reason);

    /** 仅 CREATED；重跑三重校验；before/after 快照落 order_change；差额调整合同额度（免审，Q5）。 */
    void changeOrder(Long id, OrderChangeReqVO req);

    /** 订单明细（含换算快照与来源追溯）。 */
    List<OrderItem> listItems(Long orderId);

    /** 全链路追溯：申请↔询价↔定标↔合同↔订单↔到货。 */
    Object trace(Long id);
}
