package com.dzgylxt.service.impl.budget;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.budget.BudgetProject;
import com.dzgylxt.enums.CatalogStatus;
import com.dzgylxt.mapper.budget.BudgetLineMapper;
import com.dzgylxt.mapper.budget.BudgetProjectMapper;
import com.dzgylxt.service.IBudgetProjectService;
import com.dzgylxt.vo.budget.BudgetProjectSaveReqVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** 预算项目服务实现。 */
@Service
public class BudgetProjectServiceImpl extends ServiceImpl<BudgetProjectMapper, BudgetProject>
        implements IBudgetProjectService {

    private final BudgetLineMapper budgetLineMapper;

    public BudgetProjectServiceImpl(BudgetLineMapper budgetLineMapper) {
        this.budgetLineMapper = budgetLineMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProject(BudgetProjectSaveReqVO req) {
        if (!StringUtils.hasText(req.getCode()) || !StringUtils.hasText(req.getName())) {
            throw new BizException(ResultCode.PARAM_ERROR, "项目编码与名称必填");
        }
        if (req.getYear() == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "项目年份必填");
        }
        if (baseMapper.existsByCode(req.getCode(), null)) {
            throw new BizException(ResultCode.DATA_CONFLICT, "项目编码已存在：" + req.getCode());
        }
        BudgetProject entity = new BudgetProject();
        entity.setCode(req.getCode());
        entity.setName(req.getName());
        entity.setYear(req.getYear());
        entity.setStatus(req.getStatus() == null ? CatalogStatus.VALID : req.getStatus());
        save(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProject(Long id, BudgetProjectSaveReqVO req) {
        BudgetProject entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "项目不存在：" + id);
        }
        if (StringUtils.hasText(req.getCode())
                && !req.getCode().equals(entity.getCode())
                && baseMapper.existsByCode(req.getCode(), id)) {
            throw new BizException(ResultCode.DATA_CONFLICT, "项目编码已存在：" + req.getCode());
        }
        if (StringUtils.hasText(req.getCode())) {
            entity.setCode(req.getCode());
        }
        if (StringUtils.hasText(req.getName())) {
            entity.setName(req.getName());
        }
        if (req.getYear() != null) {
            entity.setYear(req.getYear());
        }
        if (req.getStatus() != null) {
            entity.setStatus(req.getStatus());
        }
        updateById(entity);
    }

    @Override
    public void invalidate(Long id) {
        BudgetProject entity = getById(id);
        if (entity == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "项目不存在：" + id);
        }
        if (budgetLineMapper.existsByProject(id)) {
            throw new BizException(ResultCode.BIZ_ERROR, "项目被预算明细引用，不可置无效");
        }
        entity.setStatus(CatalogStatus.INVALID);
        updateById(entity);
    }

    @Override
    public List<BudgetProject> listByYear(Integer year) {
        return list(new LambdaQueryWrapper<BudgetProject>()
                .eq(year != null, BudgetProject::getYear, year)
                .orderByAsc(BudgetProject::getCode));
    }
}
