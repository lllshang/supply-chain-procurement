package com.dzgylxt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dzgylxt.entity.contract.ContractType;

/**
 * 合同类型字典服务（P4 R3a，PRD L840：被引用不可删）。
 */
public interface IContractTypeService extends IService<ContractType> {

    /** 新建类型（type_code 唯一）。 */
    Long createType(ContractType type);

    /** 编辑（名称/启停/备注）。 */
    void updateType(Long id, ContractType patch);

    /**
     * 删除（软删）：{@code contract.type_id} 引用计数 >0 → 拒绝（PRD L840）。
     */
    void deleteType(Long id);
}
