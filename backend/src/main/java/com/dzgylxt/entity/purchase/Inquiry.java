package com.dzgylxt.entity.purchase;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.InquiryStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 询价单（日常 / 框架链路可跳过）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("inquiry")
public class Inquiry extends BaseEntity implements Serializable {

    private Long applyId;
    private String inquiryNo;
    private InquiryStatus status;
    private String remark;
}
