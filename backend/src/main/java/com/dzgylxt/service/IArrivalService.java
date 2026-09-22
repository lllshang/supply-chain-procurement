package com.dzgylxt.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.order.Arrival;
import com.dzgylxt.entity.order.ArrivalItem;
import com.dzgylxt.enums.HandleType;
import com.dzgylxt.vo.order.ArrivalCreateReqVO;

import java.math.BigDecimal;
import java.util.List;

/** 到货验收服务（设计 §2.7：部分入库 / 差异 / 入库台账流水，Q8 只记台账不做 WMS 结存）。 */
public interface IArrivalService extends IService<Arrival> {

    /** 按订单展开明细 arrival_item（qty_expected=未入库余量）；生成 arrival_no；订单回写 PARTIAL_RECEIVED。 */
    Long createArrival(ArrivalCreateReqVO req);

    /** 入库确认：qty_stored 累加（基本单位）；到货单→PARTIAL_STORED/STORED；订单足额→RECEIVED。 */
    void confirmStore(Long arrivalItemId, BigDecimal qtyStored);

    /** 差异处理（接受/退货/补货）；退货/补货联动登记 fulfillment_adjust 草稿。 */
    void handleDiff(Long arrivalItemId, HandleType type);

    /** 到货明细。 */
    List<ArrivalItem> listItems(Long arrivalId);

    /** 入库台账流水（按订单/供应商/日期筛选，数据权限过滤；导出复用分页结果）。 */
    IPage<ArrivalItem> ledger(long current, long size, Long orderId, Long supplierId,
                              java.time.LocalDate from, java.time.LocalDate to);
}
