package com.dzgylxt.service.impl.contract;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.contract.ContractType;
import com.dzgylxt.mapper.contract.ContractTypeMapper;
import com.dzgylxt.service.IContractTypeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 合同类型字典服务实现（P4 R3a）。
 *
 * <p>停用后新建合同不可选，存量合同不受影响；被引用（contract.type_id）不可删（PRD L840）。</p>
 */
@Service
public class ContractTypeServiceImpl extends ServiceImpl<ContractTypeMapper, ContractType>
        implements IContractTypeService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createType(ContractType type) {
        if (!StringUtils.hasText(type.getTypeCode()) || !StringUtils.hasText(type.getTypeName())) {
            throw new BizException(ResultCode.PARAM_ERROR, "类型编码与名称必填");
        }
        long dup = count(new LambdaQueryWrapper<ContractType>()
                .eq(ContractType::getTypeCode, type.getTypeCode()));
        if (dup > 0) {
            throw new BizException(ResultCode.DATA_CONFLICT, "类型编码已存在：" + type.getTypeCode());
        }
        if (type.getEnabled() == null) {
            type.setEnabled(1);
        }
        save(type);
        return type.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateType(Long id, ContractType patch) {
        ContractType type = getById(id);
        if (type == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "合同类型不存在：" + id);
        }
        if (StringUtils.hasText(patch.getTypeName())) {
            type.setTypeName(patch.getTypeName());
        }
        if (patch.getEnabled() != null) {
            type.setEnabled(patch.getEnabled());
        }
        if (patch.getRemark() != null) {
            type.setRemark(patch.getRemark());
        }
        updateById(type);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteType(Long id) {
        ContractType type = getById(id);
        if (type == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "合同类型不存在：" + id);
        }
        // 被引用不可删（PRD L840）：contract.type_id 引用计数 >0 → 拒绝
        long refs = baseMapper.countContractRef(id);
        if (refs > 0) {
            throw new BizException(ResultCode.STATUS_INVALID,
                    "合同类型已被 " + refs + " 份合同引用，不可删除（可停用）");
        }
        removeById(id);
    }
}
