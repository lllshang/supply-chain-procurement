package com.dzgylxt.entity.order;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.AssessStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 服务验收考核（设计 §1.2.6；扣款金额供 P3 结算取数 <!-- D8 -->）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("service_assess")
public class ServiceAssess extends BaseEntity implements Serializable {

    /** 服务订单ID（order_type=1） */
    private Long orderId;
    /** 考核日期 */
    private LocalDate assessDate;
    /** 评分（口径见 Q7，一期自由录入） */
    private BigDecimal score;
    /** 扣款金额（<!-- D8: P3 结算直接取数 -->） */
    private BigDecimal deductAmount;
    /** 考核依据 */
    private String basis;
    /** 附件 JSON 数组（file_meta.file_key） */
    private String fileKeys;
    /** 0=正常 1=作废 */
    private AssessStatus status;
    private String remark;
}
