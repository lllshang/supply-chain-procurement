package com.dzgylxt.service.impl.budget;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dzgylxt.entity.budget.BudgetHeader;
import com.dzgylxt.entity.budget.BudgetLine;
import com.dzgylxt.entity.budget.BudgetProject;
import com.dzgylxt.entity.budget.BudgetSubject;
import com.dzgylxt.mapper.budget.BudgetHeaderMapper;
import com.dzgylxt.mapper.budget.BudgetLineMapper;
import com.dzgylxt.mapper.budget.BudgetProjectMapper;
import com.dzgylxt.mapper.budget.BudgetSubjectMapper;
import com.dzgylxt.service.IBudgetQueryService;
import com.dzgylxt.vo.budget.BudgetHeaderQueryReqVO;
import com.dzgylxt.vo.budget.BudgetHeaderRespVO;
import com.dzgylxt.vo.budget.BudgetLineRespVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 预算查询服务实现（部门过滤由 DataPermissionInterceptor 注入）。 */
@Service
public class BudgetQueryServiceImpl implements IBudgetQueryService {

    private final BudgetHeaderMapper budgetHeaderMapper;
    private final BudgetLineMapper budgetLineMapper;
    private final BudgetSubjectMapper budgetSubjectMapper;
    private final BudgetProjectMapper budgetProjectMapper;

    public BudgetQueryServiceImpl(BudgetHeaderMapper budgetHeaderMapper,
                                  BudgetLineMapper budgetLineMapper,
                                  BudgetSubjectMapper budgetSubjectMapper,
                                  BudgetProjectMapper budgetProjectMapper) {
        this.budgetHeaderMapper = budgetHeaderMapper;
        this.budgetLineMapper = budgetLineMapper;
        this.budgetSubjectMapper = budgetSubjectMapper;
        this.budgetProjectMapper = budgetProjectMapper;
    }

    @Override
    public IPage<BudgetHeaderRespVO> pageHeader(BudgetHeaderQueryReqVO req) {
        Page<BudgetHeader> page = new Page<>(req.getCurrent(), req.getSize());
        LambdaQueryWrapper<BudgetHeader> wrapper = new LambdaQueryWrapper<>();
        if (req.getYear() != null) {
            wrapper.eq(BudgetHeader::getYear, req.getYear());
        }
        if (req.getDeptId() != null) {
            wrapper.eq(BudgetHeader::getDeptId, req.getDeptId());
        }
        wrapper.orderByDesc(BudgetHeader::getUpdatedAt);
        IPage<BudgetHeader> result = budgetHeaderMapper.selectPage(page, wrapper);

        List<BudgetHeaderRespVO> records = new ArrayList<>();
        for (BudgetHeader header : result.getRecords()) {
            BudgetHeaderRespVO vo = new BudgetHeaderRespVO();
            vo.setId(header.getId());
            vo.setYear(header.getYear());
            vo.setDeptId(header.getDeptId());
            vo.setTotalAmount(header.getTotalAmount());
            vo.setStatus(header.getStatus() == null ? null : header.getStatus().getValue());
            vo.setRemark(header.getRemark());
            vo.setUpdatedAt(header.getUpdatedAt());
            records.add(vo);
        }
        Page<BudgetHeaderRespVO> respPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        respPage.setRecords(records);
        return respPage;
    }

    @Override
    public List<BudgetLineRespVO> listLines(Long headerId) {
        List<BudgetLine> lines = budgetLineMapper.selectByHeader(headerId);
        if (lines.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, String> subjectNames = budgetSubjectMapper.selectList(new LambdaQueryWrapper<BudgetSubject>())
                .stream().collect(Collectors.toMap(BudgetSubject::getId, BudgetSubject::getName, (a, b) -> a));
        Map<Long, String> projectNames = budgetProjectMapper.selectList(new LambdaQueryWrapper<BudgetProject>())
                .stream().collect(Collectors.toMap(BudgetProject::getId, BudgetProject::getName, (a, b) -> a));
        List<BudgetLineRespVO> result = new ArrayList<>();
        for (BudgetLine line : lines) {
            BudgetLineRespVO vo = new BudgetLineRespVO();
            vo.setId(line.getId());
            vo.setHeaderId(line.getHeaderId());
            vo.setSubjectId(line.getSubjectId());
            vo.setSubjectName(subjectNames.get(line.getSubjectId()));
            vo.setProjectId(line.getProjectId());
            vo.setProjectName(line.getProjectId() == null ? null : projectNames.get(line.getProjectId()));
            vo.setPeriod(line.getPeriod());
            vo.setAmount(line.getAmount());
            vo.setUsedAmount(line.getUsedAmount());
            vo.setVersion(line.getVersion());
            result.add(vo);
        }
        return result;
    }
}
