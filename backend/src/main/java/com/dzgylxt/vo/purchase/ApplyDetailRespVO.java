package com.dzgylxt.vo.purchase;

import com.dzgylxt.entity.purchase.PurchaseApply;
import com.dzgylxt.entity.purchase.PurchaseApplyItem;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 采购申请详情响应：头 + 明细（含换算快照列）。 */
@Data
public class ApplyDetailRespVO implements Serializable {

    private PurchaseApply apply;
    private List<PurchaseApplyItem> items = new ArrayList<>();
}
