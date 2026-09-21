package com.dzgylxt.entity;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * Outbox 事件（业务事务内同库写入，最终一致投递）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("outbox_event")
public class OutboxEvent extends BaseEntity implements Serializable {

    private String aggregate;
    private Long aggregateId;
    private String type;
    private String payloadJson;
    /** 0=PENDING，1=SENT，2=RETRY，3=FAILED */
    private Integer status;
    private Integer retry;
}
