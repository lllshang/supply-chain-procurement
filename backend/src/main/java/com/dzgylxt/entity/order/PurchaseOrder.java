package com.dzgylxt.entity.order;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.ItemType;
import com.dzgylxt.enums.OrderStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 采购订单（基于有效合同发起）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("purchase_order")
public class PurchaseOrder extends BaseEntity implements Serializable {

    private Long contractId;
    private Long applyId;
    private Long supplierId;
    private String orderNo;
    /** 0=物料 1=服务（源自申请 item_type 拆单，P2 §1.3.7） */
    private ItemType orderType;
    private OrderStatus status;
    /** 本单占用预算（P3 起为真实占用：下单时自申请转移，取消/核销经 IBudgetOccupyService） */
    private BigDecimal budgetOccupied;
    /** 阶段结算比例 JSON（如 [{"phase":1,"ratio":30},{"phase":2,"ratio":40}]；null=一次性，P3 §1.3.3） */
    private String phasePlan;
    private String remark;

    // ===== D14 日常采购「授权」控制（主流程 docx：合同+预算+授权三项控制不能省略） =====
    /** 授权人用户 ID（日常采购=订单创建人；与 BaseEntity.created_by 区分，预留委托/代理授权） */
    private Long authorizedBy;
    /** 授权人姓名/账号（冗余便于审计，不等登录态失效后丢失） */
    private String authorizedName;
    /** 授权时间（= 订单生成时间） */
    private java.time.LocalDateTime authorizedTime;
    /** 是否超单笔授权额度升级（0 否 / 1 是；超阈值→部门负责人/采购负责人确认，DAILY_AUTH 审批任务） */
    private Integer authOverLimit;
    /** D17：是否由系统自动授权通过（0 否 / 1 是；高频补货自动授权开关开启且日常采购时置 1，区别于 authOverLimit 的人工确认） */
    private Integer autoAuthorized;

    // ===== D16 无申请来源订单「需求来源」留痕（主流程 docx：必须保留需求来源） =====
    /** 需求来源类型：APPLY=采购申请来源 / OFFLINE=无申请来源（日常采购/框架合同直发）；由 applyId 推导，不接收客户端输入 */
    private String sourceType;
    /** 需求来源说明（applyId==null 时必填，记录为何发起本次采购，供报表/审计追溯） */
    private String sourceReason;

    /** R5：派生展示字段——结清进度 = Σ有效结算金额 / 应结总额（0~1）；不参与订单状态机。 */
    @TableField(exist = false)
    private BigDecimal settleProgress;
    /** R5：派生展示字段——付清进度 = Σ已付付款金额 / 应结总额（0~1）；不参与订单状态机。 */
    @TableField(exist = false)
    private BigDecimal paidProgress;
}
