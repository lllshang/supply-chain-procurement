package com.dzgylxt.entity.approval;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 审批流定义（P4 表驱动硬约束，规格 §3.1）。
 *
 * <p>每个 bizType 一行；{@code flow_version} 随配置变更递增，仅审计对账用，
 * 运行期读 {@link ApprovalNodeDef} + 任务节点快照（设计 §2.9）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("approval_flow_def")
public class ApprovalFlowDef extends BaseEntity implements Serializable {

    /** 流程键（=bizType，8 个：PURCHASE_APPLY/AWARD/CONTRACT/FULFILLMENT_ADJUST/BUDGET/SETTLEMENT/SUPPLIER_QUAL/DAILY_AUTH） */
    private String flowKey;
    /** 业务类型（与 flowKey 同值，显式冗余便于查询） */
    private String bizType;
    /** 流程版本（每次配置变更 +1） */
    private Integer flowVersion;
    /** 流程名称 */
    private String flowName;
    /** 1=启用 0=停用（停用后该 bizType 新任务拒绝创建，在途不受影响） */
    private Integer enabled;
    /** 备注 */
    private String remark;
}
