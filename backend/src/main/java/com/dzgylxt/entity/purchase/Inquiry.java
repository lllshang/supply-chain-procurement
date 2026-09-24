package com.dzgylxt.entity.purchase;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.InquiryStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 询价单（日常 / 框架链路可跳过）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("inquiry")
public class Inquiry extends BaseEntity implements Serializable {

    private Long applyId;
    /** 询价来源：APPLY=申请转询价 / OFFLINE=独立寻源（D9，BR-07） */
    private String sourceType;
    /** 寻源原因（source_type=OFFLINE 必填，BR-07 L1082） */
    private String sourceReason;
    private String inquiryNo;
    /** 截标时间（到期或手动截标→CLOSED，P2 §1.3.3） */
    private LocalDateTime deadline;
    /** 创建部门（数据权限，P2 §1.3.3） */
    private Long createdByDept;
    private InquiryStatus status;
    private String remark;
}
