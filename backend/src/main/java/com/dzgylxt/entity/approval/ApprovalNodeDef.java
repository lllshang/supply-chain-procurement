package com.dzgylxt.entity.approval;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 审批节点定义（P4 表驱动：节点/角色/金额区间全配置化）。
 *
 * <p>金额区间语义（设计 §1.2.2）：任务创建时按 {@code payloadJson.amount}（无金额视为 NULL）
 * 取节点集——命中条件 {@code (amount_min IS NULL OR amount >= amount_min)
 * AND (amount_max IS NULL OR amount < amount_max)}，命中节点按 {@code seq} 升序构成节点链。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("approval_node_def")
public class ApprovalNodeDef extends BaseEntity implements Serializable {

    /** 所属流程（=approval_flow_def.flow_key） */
    private String flowKey;
    /** 节点编码（流程内唯一，如 N1/N2） */
    private String nodeCode;
    /** 节点名称（工作台展示） */
    private String nodeName;
    /** 节点序（1 起，按 seq 升序流转） */
    private Integer seq;
    /** 审批人解析类型：ROLE / DEPT_HEAD_OF_APPLICANT / USER */
    private String approverType;
    /** ROLE=角色编码（sys_role.role_code）；DEPT_HEAD_OF_APPLICANT=置 NULL；USER=用户名（兜底慎用） */
    private String approverValue;
    /** 节点生效金额区间下界（含）；NULL=无下界 */
    private BigDecimal amountMin;
    /** 节点生效金额区间上界（不含）；NULL=无上界；双 NULL=恒生效 */
    private BigDecimal amountMax;
    /** 签类型：ANY=或签（默认）/ ALL=会签（预留，Q13 未拍板不启用） */
    private String signType;
    /** 超时升级时限（预留位不实现，v1.x） */
    private Integer timeoutHours;
    /** 免审规则位（0=必审；本期全部必审，Q7 固化） */
    private Integer freeReview;
    /** 1=启用 0=停用 */
    private Integer enabled;
    /** 备注 */
    private String remark;
}
