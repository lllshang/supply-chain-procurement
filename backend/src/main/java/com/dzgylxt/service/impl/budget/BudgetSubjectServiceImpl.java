package com.dzgylxt.service.impl.budget;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.common.TreeUtils;
import com.dzgylxt.entity.budget.BudgetSubject;
import com.dzgylxt.enums.SubjectType;
import com.dzgylxt.enums.CatalogStatus;
import com.dzgylxt.mapper.budget.BudgetLineMapper;
import com.dzgylxt.mapper.budget.BudgetSubjectMapper;
import com.dzgylxt.service.IBudgetSubjectService;
import com.dzgylxt.vo.budget.BudgetSubjectSaveReqVO;
import com.dzgylxt.vo.common.CategoryTreeNodeVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** 预算科目服务实现。 */
@Service
public class BudgetSubjectServiceImpl extends ServiceImpl<BudgetSubjectMapper, BudgetSubject>
        implements IBudgetSubjectService {

    private final BudgetLineMapper budgetLineMapper;

    public BudgetSubjectServiceImpl(BudgetLineMapper budgetLineMapper) {
        this.budgetLineMapper = budgetLineMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSubject(BudgetSubjectSaveReqVO req) {
        if (!StringUtils.hasText(req.getCode()) || !StringUtils.hasText(req.getName())) {
            throw new BizException(ResultCode.PARAM_ERROR, "科目编码与名称必填");
        }
        if (baseMapper.existsByCode(req.getCode(), null)) {
            throw new BizException(ResultCode.DATA_CONFLICT, "科目编码已存在：" + req.getCode());
        }
        BudgetSubject entity = new BudgetSubject();
        entity.setCode(req.getCode());
        entity.setName(req.getName());
        entity.setParentId(req.getParentId() == null ? 0L : req.getParentId());
        entity.setSubjectType(toSubjectType(req.getSubjectType()));
        entity.setStatus(req.getStatus() == null ? CatalogStatus.VALID : req.getStatus());
        save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSubject(Long id, BudgetSubjectSaveReqVO req) {
        BudgetSubject entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "科目不存在：" + id);
        }
        if (StringUtils.hasText(req.getCode())
                && !req.getCode().equals(entity.getCode())
                && baseMapper.existsByCode(req.getCode(), id)) {
            throw new BizException(ResultCode.DATA_CONFLICT, "科目编码已存在：" + req.getCode());
        }
        if (StringUtils.hasText(req.getCode())) {
            entity.setCode(req.getCode());
        }
        if (StringUtils.hasText(req.getName())) {
            entity.setName(req.getName());
        }
        if (req.getParentId() != null) {
            entity.setParentId(req.getParentId());
        }
        if (req.getSubjectType() != null) {
            entity.setSubjectType(toSubjectType(req.getSubjectType()));
        }
        if (req.getStatus() != null) {
            entity.setStatus(req.getStatus());
        }
        updateById(entity);
    }

    @Override
    public List<CategoryTreeNodeVO> tree() {
        List<BudgetSubject> all = list(new LambdaQueryWrapper<BudgetSubject>().orderByAsc(BudgetSubject::getId));
        List<CategoryTreeNodeVO> nodes = new ArrayList<>();
        for (BudgetSubject s : all) {
            CategoryTreeNodeVO node = new CategoryTreeNodeVO();
            node.setId(s.getId());
            node.setParentId(s.getParentId());
            node.setCode(s.getCode());
            node.setName(s.getName());
            node.setStatus(s.getStatus());
            nodes.add(node);
        }
        return TreeUtils.build(nodes);
    }

    @Override
    public void invalidate(Long id) {
        BudgetSubject entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "科目不存在：" + id);
        }
        if (budgetLineMapper.existsBySubject(id)) {
            throw new BizException(ResultCode.BIZ_ERROR, "科目被预算明细引用，不可置无效");
        }
        entity.setStatus(CatalogStatus.INVALID);
        updateById(entity);
    }

    private SubjectType toSubjectType(Integer value) {
        if (value == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "科目类型必填");
        }
        for (SubjectType type : SubjectType.values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        throw new BizException(ResultCode.PARAM_ERROR, "未知科目类型：" + value);
    }
}
