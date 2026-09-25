package com.dzgylxt.entity.contract;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dzgylxt.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 合同类型字典（R3a，规格 §5.1 / PRD L840：被引用不可删）。
 *
 * <p>存量 {@code contract.contract_type} TINYINT（0=物料 1=服务 2=综合）与本字典并存，
 * {@code contract.type_id} 优先。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("contract_type")
public class ContractType extends BaseEntity implements Serializable {

    /** 类型编码（MATERIAL/SERVICE/MIXED…） */
    private String typeCode;
    /** 类型名称 */
    private String typeName;
    /** 1=启用 0=停用（停用后新建合同不可选，存量不受影响） */
    private Integer enabled;
    /** 备注 */
    private String remark;
}
