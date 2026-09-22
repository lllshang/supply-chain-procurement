package com.dzgylxt.vo.order;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 服务验收考核保存请求（设计 §2.7；扣款供 P3 结算取数 <!-- D8 -->）。 */
@Data
public class ServiceAssessSaveReqVO implements Serializable {

    /** 服务订单ID（order_type=1） */
    private Long orderId;
    private LocalDate assessDate;
    private BigDecimal score;
    private BigDecimal deductAmount;
    private String basis;
    /** 附件 JSON 数组（file_meta.file_key） */
    private List<String> fileKeys;
    private String remark;
}
