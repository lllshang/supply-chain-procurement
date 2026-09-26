package com.dzgylxt.entity.contract;

import com.dzgylxt.common.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.enums.ContractStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 合同（仅 EFFECTIVE 且在有效期可发起订单）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("contract")
public class Contract extends BaseEntity implements Serializable {

    private Long supplierId;
    /** 来源定标（<!-- D2: P2b 线下补录可空 -->，P2 §1.3.6） */
    private Long awardId;
    private String no;
    private String title;
    /** 0=物料 1=服务 2=综合（P2 新增） */
    private Integer contractType;
    private BigDecimal amount;
    private LocalDate validFrom;
    private LocalDate validTo;
    /** 附件 JSON 数组（file_meta.file_key，多附件，P2 新增） */
    private String fileKeys;
    /** 续签来源合同（<!-- D1: P2b 框架续签 -->，P2 新增） */
    private Long renewedFromId;
    /** 关联原合同（P4 R3a：补充签订 SUPPLEMENT 指向原合同；续签沿用 renewedFromId 不占本列） */
    private Long sourceContractId;
    /** 关联类型：SUPPLEMENT=补充签订（P4 R3a） */
    private String relationType;
    /** 合同类型字典引用（P4 R3a；与存量 contractType TINYINT 并存，typeId 优先） */
    private Long typeId;
    /** 预算科目（S8：统计冗余，预算锚点仍=申请/award，P2b） */
    private Long subjectId;
    /** G2：经办部门 */
    private String ownerDept;
    /** G2：经办人 */
    private String owner;
    /** G2：签订日期 */
    private LocalDate signDate;
    /** G2：关联项目（可选） */
    private Long projectId;
    /** 终止原因（P2 新增） */
    private String terminateReason;
    private ContractStatus status;
    private BigDecimal availableAmount;
    private String remark;
    /** 乐观锁版本（额度扣减并发兜底，主防=行锁，P2 新增） */
    private Integer version;
}
