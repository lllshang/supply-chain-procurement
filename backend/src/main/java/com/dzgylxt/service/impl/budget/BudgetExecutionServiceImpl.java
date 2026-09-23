package com.dzgylxt.service.impl.budget;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.entity.budget.BudgetLine;
import com.dzgylxt.entity.budget.BudgetOccupyLog;
import com.dzgylxt.enums.BudgetAction;
import com.dzgylxt.enums.BudgetBizType;
import com.dzgylxt.mapper.budget.BudgetLineMapper;
import com.dzgylxt.mapper.budget.BudgetOccupyLogMapper;
import com.dzgylxt.security.UserContext;
import com.dzgylxt.service.IBudgetExecutionService;
import com.dzgylxt.vo.budget.BudgetOccupyCmd;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 预算执行台账查询服务（P3 设计 T03）。只读为主；{@link #recalibrate} 为运维校准
 * 例外入口——差值以 ADJUST log 补账保持 {@code Σlog == used_amount} 守恒。
 */
@Service
public class BudgetExecutionServiceImpl implements IBudgetExecutionService {

    @Autowired
    private BudgetLineMapper budgetLineMapper;

    @Autowired
    private com.dzgylxt.mapper.budget.BudgetHeaderMapper budgetHeaderMapper;

    @Autowired
    private BudgetOccupyLogMapper occupyLogMapper;

    @Override
    public List<Row> ledger(Long deptId, Integer year) {
        // BudgetLine 无 deptId/year 直存（经 budget_header 关联），先定位头部 id 集合
        List<Long> headerIds;
        if (deptId != null || year != null) {
            headerIds = budgetHeaderMapper.selectList(
                            new LambdaQueryWrapper<com.dzgylxt.entity.budget.BudgetHeader>()
                                    .eq(deptId != null,
                                            com.dzgylxt.entity.budget.BudgetHeader::getDeptId, deptId)
                                    .eq(year != null,
                                            com.dzgylxt.entity.budget.BudgetHeader::getYear, year))
                    .stream().map(com.dzgylxt.entity.budget.BudgetHeader::getId).toList();
            if (headerIds.isEmpty()) {
                return new ArrayList<>();
            }
        } else {
            headerIds = null;
        }
        LambdaQueryWrapper<BudgetLine> wrapper = new LambdaQueryWrapper<>();
        if (headerIds != null) {
            wrapper.in(BudgetLine::getHeaderId, headerIds);
        }
        // R1：仅月度行（period 1–12）参与台账——period=0 年度额度行已拆除，
        // 年度 = 12 个月度行聚合视图（历史存量行被过滤，不重复计入可用/执行率）
        wrapper.ne(BudgetLine::getPeriod, 0);
        List<BudgetLine> lines = budgetLineMapper.selectList(
                wrapper.orderByAsc(BudgetLine::getSubjectId).orderByAsc(BudgetLine::getPeriod));
        List<Row> rows = new ArrayList<>();
        for (BudgetLine line : lines) {
            Row row = new Row();
            org.springframework.beans.BeanUtils.copyProperties(line, row);
            BigDecimal amount = line.getAmount() == null ? BigDecimal.ZERO : line.getAmount();
            BigDecimal used = line.getUsedAmount() == null ? BigDecimal.ZERO : line.getUsedAmount();
            row.setExecRate(amount.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                    : used.multiply(new BigDecimal("100")).divide(amount, 2, RoundingMode.HALF_UP));
            rows.add(row);
        }
        return rows;
    }

    @Override
    public IPage<BudgetOccupyLog> logs(long current, long size, Long budgetLineId,
                                       BudgetBizType bizType, Long bizId) {
        LambdaQueryWrapper<BudgetOccupyLog> wrapper = new LambdaQueryWrapper<>();
        if (budgetLineId != null) {
            wrapper.eq(BudgetOccupyLog::getBudgetLineId, budgetLineId);
        }
        if (bizType != null) {
            wrapper.eq(BudgetOccupyLog::getBizType, bizType);
        }
        if (bizId != null) {
            wrapper.eq(BudgetOccupyLog::getBizId, bizId);
        }
        return occupyLogMapper.selectPage(new Page<>(current, size),
                wrapper.orderByDesc(BudgetOccupyLog::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recalibrate(Long budgetLineId) {
        BudgetLine line = budgetLineMapper.selectById(budgetLineId);
        if (line == null) {
            throw new BizException(ResultCode.NOT_FOUND, "预算行不存在：" + budgetLineId);
        }
        BigDecimal net = occupyLogMapper.sumNetOccupiedByLine(budgetLineId);
        BigDecimal used = line.getUsedAmount() == null ? BigDecimal.ZERO : line.getUsedAmount();
        BigDecimal diff = net.subtract(used);
        if (diff.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        // 校准例外：直接以 version 条件更新对齐 Σlog，并补 ADJUST 差值 log（守恒恢复）
        int updated = budgetLineMapper.changeUsedAmount(budgetLineId, diff, line.getVersion());
        if (updated == 0) {
            BudgetLine fresh = budgetLineMapper.selectById(budgetLineId);
            updated = budgetLineMapper.changeUsedAmount(budgetLineId, diff, fresh.getVersion());
        }
        if (updated == 0) {
            throw new BizException(ResultCode.DATA_CONFLICT, "校准并发冲突，请重试");
        }
        BudgetOccupyLog entry = new BudgetOccupyLog();
        entry.setBudgetLineId(budgetLineId);
        entry.setBizType(BudgetBizType.ADJUST);
        entry.setBizId(budgetLineId);
        entry.setAction(BudgetAction.ADJUST);
        entry.setAmount(diff);
        entry.setBalanceBefore(used);
        entry.setBalanceAfter(used.add(diff));
        entry.setOperator(UserContext.getCurrentUserId());
        entry.setRemark("人工校准：Σlog=" + net + "，used=" + used);
        occupyLogMapper.insert(entry);
    }
}
