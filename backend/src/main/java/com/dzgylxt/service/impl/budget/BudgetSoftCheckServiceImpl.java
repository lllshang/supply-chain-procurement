package com.dzgylxt.service.impl.budget;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dzgylxt.entity.budget.BudgetHeader;
import com.dzgylxt.entity.budget.BudgetLine;
import com.dzgylxt.mapper.budget.BudgetHeaderMapper;
import com.dzgylxt.mapper.budget.BudgetLineMapper;
import com.dzgylxt.service.IBudgetSoftCheckService;
import com.dzgylxt.vo.purchase.BudgetCheckResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;

/**
 * 预算校验实现（P3 升级为月度硬控读数，设计 §2.3 <!-- D3: 已落地 P3 -->）。
 *
 * <p>契约不变（P2 入口零改动）；读数语义升级：按 <b>提交当月</b> 取
 * {@code budget_line} 月度行（period=1–12，部门×科目×月份控制单元，Q1b 定稿），
 * 可用余额 = Σ(该控制单元月度行 amount − used_amount)；<b>月度行不存在 = 无预算
 * （budgetStatus=2，走升级审批）</b>；年度行（period=0）仅台账汇总展示。
 * 真实占用/释放/核销一律经 {@link IBudgetOccupyService} 唯一写入口。</p>
 */
@Service
public class BudgetSoftCheckServiceImpl implements IBudgetSoftCheckService {

    @Autowired
    private BudgetHeaderMapper budgetHeaderMapper;

    @Autowired
    private BudgetLineMapper budgetLineMapper;

    @Override
    public BudgetCheckResultVO check(Long deptId, Integer year, Long subjectId, BigDecimal amount) {
        int y = year == null ? Year.now().getValue() : year;
        int month = LocalDate.now().getMonthValue();
        BudgetCheckResultVO vo = new BudgetCheckResultVO();
        vo.setAmount(amount);

        List<BudgetHeader> headers = budgetHeaderMapper.selectList(
                Wrappers.<BudgetHeader>lambdaQuery()
                        .eq(BudgetHeader::getDeptId, deptId)
                        .eq(BudgetHeader::getYear, y));
        if (headers.isEmpty()) {
            vo.setBudgetStatus(2);
            vo.setBalance(null);
            vo.setMessage("无 " + y + " 年度预算头，需预算升级审批");
            return vo;
        }
        BigDecimal balance = BigDecimal.ZERO;
        boolean hasMonthlyLine = false;
        for (BudgetHeader header : headers) {
            for (BudgetLine line : budgetLineMapper.selectByHeader(header.getId())) {
                if (line.getPeriod() == null || line.getPeriod() != month) {
                    continue; // 仅月度行参与硬控读数；年度行/其他月不拦截当月
                }
                if (subjectId != null && !subjectId.equals(line.getSubjectId())) {
                    continue;
                }
                hasMonthlyLine = true;
                balance = balance.add(
                        (line.getAmount() == null ? BigDecimal.ZERO : line.getAmount())
                                .subtract(line.getUsedAmount() == null ? BigDecimal.ZERO : line.getUsedAmount()));
            }
        }
        vo.setBalance(balance);
        if (!hasMonthlyLine) {
            vo.setBudgetStatus(2);
            vo.setMessage("无当月（" + month + "月）预算行 = 无预算，需预算升级审批");
            return vo;
        }
        if (amount != null && balance.compareTo(amount) < 0) {
            vo.setBudgetStatus(2);
            vo.setMessage("当月预算余额 " + balance + " 不足，需预算升级审批");
        } else {
            vo.setBudgetStatus(1);
            vo.setMessage("当月预算校验通过（部门×科目×月份控制单元）");
        }
        return vo;
    }
}
