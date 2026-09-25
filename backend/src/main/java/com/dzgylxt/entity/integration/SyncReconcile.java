package com.dzgylxt.entity.integration;

import com.dzgylxt.common.BaseEntity;
import com.dzgylxt.enums.SyncStatus;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 同步对账（幂等 / 对账，P1）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sync_reconcile")
public class SyncReconcile extends BaseEntity implements Serializable {

    private String eventId;
    private String target;
    private SyncStatus status;
    private String lastResp;
}
