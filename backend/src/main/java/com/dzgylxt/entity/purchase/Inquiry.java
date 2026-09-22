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
    private String inquiryNo;
    /** 截标时间（到期或手动截标→CLOSED，P2 §1.3.3） */
    private LocalDateTime deadline;
    /** 创建部门（数据权限，P2 §1.3.3） */
    private Long createdByDept;
    private InquiryStatus status;
    private String remark;
}
