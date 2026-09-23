package com.dzgylxt.service.impl.budget;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dzgylxt.common.BizException;
import com.dzgylxt.common.ResultCode;
import com.dzgylxt.common.TaskRegistry;
import com.dzgylxt.entity.budget.BudgetHeader;
import com.dzgylxt.entity.budget.BudgetLine;
import com.dzgylxt.entity.budget.BudgetProject;
import com.dzgylxt.entity.budget.BudgetSubject;
import com.dzgylxt.enums.BudgetHeaderStatus;
import com.dzgylxt.mapper.budget.BudgetHeaderMapper;
import com.dzgylxt.mapper.budget.BudgetLineMapper;
import com.dzgylxt.mapper.budget.BudgetProjectMapper;
import com.dzgylxt.mapper.budget.BudgetSubjectMapper;
import com.dzgylxt.service.IBudgetImportService;
import com.dzgylxt.vo.budget.BudgetImportPreviewVO;
import com.dzgylxt.vo.budget.BudgetImportRowVO;
import com.dzgylxt.vo.budget.BudgetLineRespVO;
import com.dzgylxt.vo.common.ImportErrorVO;
import com.dzgylxt.vo.common.ImportTaskVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 年度预算导入服务实现（R-BUD-03，R1 修订）。
 *
 * <p>解析模板 → 校验（年度/部门/科目/金额）→ 生成 1 条预算头 + N 条<b>月度</b>明细
 * （科目 × 月份 1–12，period=0 年度额度行<b>已拆除（R1）</b>——年度 = 12 个月度行聚合视图；
 * project_id 退出额度控制（R1），仅作为统计冗余随行落库）。
 * 全部校验通过才落库；失败回传错误清单（定位到行/列）。</p>
 */
@Service
public class BudgetImportServiceImpl implements IBudgetImportService {

    /** 当前模板版本。 */
    private static final String TEMPLATE_VERSION = "v1";

    private final BudgetHeaderMapper budgetHeaderMapper;
    private final BudgetLineMapper budgetLineMapper;
    private final BudgetSubjectMapper budgetSubjectMapper;
    private final BudgetProjectMapper budgetProjectMapper;
    private final TaskRegistry taskRegistry;

    public BudgetImportServiceImpl(BudgetHeaderMapper budgetHeaderMapper,
                                   BudgetLineMapper budgetLineMapper,
                                   BudgetSubjectMapper budgetSubjectMapper,
                                   BudgetProjectMapper budgetProjectMapper,
                                   TaskRegistry taskRegistry) {
        this.budgetHeaderMapper = budgetHeaderMapper;
        this.budgetLineMapper = budgetLineMapper;
        this.budgetSubjectMapper = budgetSubjectMapper;
        this.budgetProjectMapper = budgetProjectMapper;
        this.taskRegistry = taskRegistry;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportTaskVO importAnnual(MultipartFile file) {
        Parsed parsed = parseAndValidate(file);
        ImportTaskVO task = new ImportTaskVO();
        task.setTaskId(taskRegistry.nextId());
        task.setTotalRows(parsed.totalRows);
        task.setErrorRows(parsed.errors.size());
        task.setErrors(parsed.errors);
        if (!parsed.errors.isEmpty()) {
            task.setStatus("FAILED");
            task.setSuccess(false);
            taskRegistry.putTask(task);
            return task;
        }
        // 落库：1 条 header + N 条 line
        BudgetHeader header = new BudgetHeader();
        header.setYear(parsed.year);
        header.setDeptId(parsed.deptId);
        header.setTotalAmount(parsed.totalAmount);
        header.setStatus(BudgetHeaderStatus.DRAFT);
        header.setRemark("年度预算导入（模板 " + TEMPLATE_VERSION + "）");
        budgetHeaderMapper.insert(header);
        for (LineDraft draft : parsed.drafts.values()) {
            // R1：仅落月度行（period 1–12）；年度=聚合视图，不落 period=0 额度行
            for (int period = 1; period <= 12; period++) {
                BudgetLine line = new BudgetLine();
                line.setHeaderId(header.getId());
                line.setSubjectId(draft.subjectId);
                line.setProjectId(draft.statProjectId());
                line.setPeriod(period);
                line.setAmount(draft.amounts[period]);
                line.setUsedAmount(BigDecimal.ZERO);
                line.setVersion(0);
                budgetLineMapper.insert(line);
            }
        }
        task.setStatus("SUCCESS");
        task.setSuccess(true);
        taskRegistry.putTask(task);
        return task;
    }

    @Override
    public BudgetImportPreviewVO preview(MultipartFile file) {
        Parsed parsed = parseAndValidate(file);
        BudgetImportPreviewVO preview = new BudgetImportPreviewVO();
        preview.setTemplateVersion(TEMPLATE_VERSION);
        preview.setYear(parsed.year);
        preview.setDeptId(parsed.deptId);
        preview.setTotalAmount(parsed.totalAmount);
        preview.setErrors(parsed.errors);
        preview.setValid(parsed.errors.isEmpty());
        Map<Long, String> subjectNames = subjectNameMap();
        Map<Long, String> projectNames = projectNameMap();
        List<BudgetLineRespVO> lines = new ArrayList<>();
        for (LineDraft draft : parsed.drafts.values()) {
            // R1：预览仅月度行（period 1–12），与落库口径一致
            for (int period = 1; period <= 12; period++) {
                BudgetLineRespVO vo = new BudgetLineRespVO();
                vo.setSubjectId(draft.subjectId);
                vo.setSubjectName(subjectNames.get(draft.subjectId));
                vo.setProjectId(draft.statProjectId());
                vo.setProjectName(draft.statProjectId() == null ? null : projectNames.get(draft.statProjectId()));
                vo.setPeriod(period);
                vo.setAmount(draft.amounts[period]);
                vo.setUsedAmount(BigDecimal.ZERO);
                lines.add(vo);
            }
        }
        preview.setLines(lines);
        return preview;
    }

    @Override
    public ImportTaskVO taskStatus(String taskId) {
        ImportTaskVO task = taskRegistry.getTask(taskId);
        if (task == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "导入任务不存在：" + taskId);
        }
        return task;
    }

    // ---------------- 内部解析与校验 ----------------

    private Parsed parseAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "上传文件为空");
        }
        List<BudgetImportRowVO> rows;
        try {
            rows = EasyExcel.read(file.getInputStream()).head(BudgetImportRowVO.class).sheet().doReadSync();
        } catch (IOException e) {
            throw new BizException(ResultCode.BIZ_ERROR, "文件解析失败：" + e.getMessage());
        }
        Parsed parsed = new Parsed();
        parsed.totalRows = rows.size();
        if (rows.isEmpty()) {
            parsed.errors.add(new ImportErrorVO(1, "文件", "无有效数据行"));
            return parsed;
        }
        Map<String, BudgetSubject> subjects = budgetSubjectMapper
                .selectList(new LambdaQueryWrapper<BudgetSubject>())
                .stream().collect(Collectors.toMap(BudgetSubject::getCode, Function.identity(), (a, b) -> a));
        Map<String, BudgetProject> projects = budgetProjectMapper
                .selectList(new LambdaQueryWrapper<BudgetProject>())
                .stream().collect(Collectors.toMap(BudgetProject::getCode, Function.identity(), (a, b) -> a));

        BudgetImportRowVO first = rows.get(0);
        parsed.year = first.getYear();
        parsed.deptId = first.getDeptId();

        for (int i = 0; i < rows.size(); i++) {
            BudgetImportRowVO row = rows.get(i);
            int rowNo = i + 2;
            boolean ok = true;
            if (row.getYear() == null) {
                parsed.errors.add(new ImportErrorVO(rowNo, "年份", "年份必填"));
                ok = false;
            } else if (parsed.year != null && !row.getYear().equals(parsed.year)) {
                parsed.errors.add(new ImportErrorVO(rowNo, "年份", "同一批导入年份须一致"));
                ok = false;
            }
            if (row.getDeptId() == null) {
                parsed.errors.add(new ImportErrorVO(rowNo, "部门ID", "部门必填"));
                ok = false;
            } else if (parsed.deptId != null && !row.getDeptId().equals(parsed.deptId)) {
                parsed.errors.add(new ImportErrorVO(rowNo, "部门ID", "同一批导入部门须一致"));
                ok = false;
            }
            BudgetSubject subject = null;
            if (!StringUtils.hasText(row.getSubjectCode())) {
                parsed.errors.add(new ImportErrorVO(rowNo, "科目编码", "科目编码必填"));
                ok = false;
            } else {
                subject = subjects.get(row.getSubjectCode());
                if (subject == null) {
                    parsed.errors.add(new ImportErrorVO(rowNo, "科目编码", "科目不存在：" + row.getSubjectCode()));
                    ok = false;
                }
            }
            Long projectId = null;
            if (StringUtils.hasText(row.getProjectCode())) {
                BudgetProject project = projects.get(row.getProjectCode());
                if (project == null) {
                    parsed.errors.add(new ImportErrorVO(rowNo, "项目编码", "项目不存在：" + row.getProjectCode()));
                    ok = false;
                } else {
                    projectId = project.getId();
                }
            }
            if (!validateAmounts(row, rowNo, parsed.errors)) {
                ok = false;
            }
            if (ok && subject != null) {
                mergeDraft(parsed, subject.getId(), projectId, row);
            }
        }
        parsed.totalAmount = parsed.drafts.values().stream()
                .flatMap(d -> java.util.Arrays.stream(d.amounts).skip(1)) // R1：年度总额 = 12 个月度行聚合
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return parsed;
    }

    private boolean validateAmounts(BudgetImportRowVO row, int rowNo, List<ImportErrorVO> errors) {
        boolean ok = true;
        if (row.getAnnual() != null && row.getAnnual().compareTo(BigDecimal.ZERO) < 0) {
            errors.add(new ImportErrorVO(rowNo, "年度总额", "金额不可为负"));
            ok = false;
        }
        for (int m = 1; m <= 12; m++) {
            BigDecimal amount = row.monthAt(m);
            if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) {
                errors.add(new ImportErrorVO(rowNo, m + "月", "金额不可为负"));
                ok = false;
            }
        }
        return ok;
    }

    /**
     * 合并行草稿（R1：key 仅科目——project_id 退出额度控制，不再按项目拆额度行；
     * 项目仅作统计冗余，同科目多项目混合时 projectId 置空避免误归属）。
     */
    private void mergeDraft(Parsed parsed, Long subjectId, Long projectId, BudgetImportRowVO row) {
        LineDraft draft = parsed.drafts.computeIfAbsent(subjectId, k -> new LineDraft(subjectId));
        draft.mergeProject(projectId);
        BigDecimal monthSum = BigDecimal.ZERO;
        for (int m = 1; m <= 12; m++) {
            BigDecimal amount = row.monthAt(m);
            if (amount == null) {
                amount = BigDecimal.ZERO;
            }
            draft.amounts[m] = draft.amounts[m].add(amount);
            monthSum = monthSum.add(amount);
        }
        // 年度总额列仅为模板信息列（R1：无 period=0 行），月度合计为权威口径
    }

    private Map<Long, String> subjectNameMap() {
        return budgetSubjectMapper.selectList(new LambdaQueryWrapper<BudgetSubject>())
                .stream().collect(Collectors.toMap(BudgetSubject::getId, BudgetSubject::getName, (a, b) -> a));
    }

    private Map<Long, String> projectNameMap() {
        return budgetProjectMapper.selectList(new LambdaQueryWrapper<BudgetProject>())
                .stream().collect(Collectors.toMap(BudgetProject::getId, BudgetProject::getName, (a, b) -> a));
    }

    /** 解析结果holder。 */
    private static final class Parsed {
        private Integer totalRows = 0;
        private Integer year;
        private Long deptId;
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private final List<ImportErrorVO> errors = new ArrayList<>();
        private final Map<Long, LineDraft> drafts = new LinkedHashMap<>();
    }

    /** 明细草稿（R1）：amounts[1–12]=月度额度（无年度行）；projectId 仅统计冗余（混合即置空）。 */
    private static final class LineDraft {
        private final Long subjectId;
        private Long projectId;
        private boolean projectSeen;
        private boolean projectConflict;
        private final BigDecimal[] amounts = new BigDecimal[13];

        private LineDraft(Long subjectId) {
            this.subjectId = subjectId;
            for (int i = 0; i < amounts.length; i++) {
                amounts[i] = BigDecimal.ZERO;
            }
        }

        /** 项目统计归属：一致则保留，混合（含有无混杂）置空——project_id 不参与额度控制。 */
        private void mergeProject(Long projectId) {
            if (!projectSeen) {
                projectSeen = true;
                this.projectId = projectId;
            } else if ((this.projectId == null) != (projectId == null)
                    || (this.projectId != null && !this.projectId.equals(projectId))) {
                projectConflict = true;
            }
        }

        private Long statProjectId() {
            return projectConflict ? null : projectId;
        }
    }
}
