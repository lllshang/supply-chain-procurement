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
import java.time.Year;
import java.util.List;

/**
 * 预算软校验实现（设计 §2.1 <!-- D3 -->）。
 *
 * <p>只读：按部门 + 年度取预算头，汇总其 budget_line 的 {@code Σamount − Σused}
 * 得到余额；与申请金额比较，不足时 {@code budgetStatus=2} 仅提示。无预算数据时
 * 视为通过（budgetStatus=1，balance=null，提示"无预算数据"）。</p>
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
        BudgetCheckResultVO vo = new BudgetCheckResultVO();
        vo.setAmount(amount);

        List<BudgetHeader> headers = budgetHeaderMapper.selectList(
                Wrappers.<BudgetHeader>lambdaQuery()
                        .eq(BudgetHeader::getDeptId, deptId)
                        .eq(BudgetHeader::getYear, y));
        if (headers.isEmpty()) {
            vo.setBudgetStatus(1);
            vo.setBalance(null);
            vo.setMessage("无预算数据，软校验通过");
            return vo;
        }

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal used = BigDecimal.ZERO;
        boolean matched = subjectId == null;
        for (BudgetHeader header : headers) {
            for (BudgetLine line : budgetLineMapper.selectByHeader(header.getId())) {
                total = total.add(line.getAmount() == null ? BigDecimal.ZERO : line.getAmount());
                used = used.add(line.getUsedAmount() == null ? BigDecimal.ZERO : line.getUsedAmount());
                if (subjectId != null && subjectId.equals(line.getSubjectId())) {
                    matched = true;
                }
            }
        }
        BigDecimal balance = total.subtract(used);
        vo.setBalance(balance);

        // 超预算判定：申请金额 > 余额（科目维度未命中时同样按总额口径提示）
        if (amount != null && balance.compareTo(amount) < 0) {
            vo.setBudgetStatus(2);
            vo.setMessage("申请金额超出年度预算余额 " + balance + "，仅提示不拦截（P3 硬控制 <!-- D3 -->）");
        } else {
            vo.setBudgetStatus(1);
            vo.setMessage(matched ? "预算校验通过" : "预算校验通过（未命中指定科目，按部门总额口径）");
        }
        return vo;
    }
}
