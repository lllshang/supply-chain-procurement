<template>
  <el-card>
    <div class="tip-box">
      年度预算导入：一行 = 一个「科目×项目」，列口径为 年度总额 + 1–12 月。
      上传后先预览校验，通过后确认导入（异步任务，轮询进度）。
    </div>

    <ImportWizard
      :preview-api="previewBudgetImport"
      :submit-api="importBudget"
      :task-api="budgetImportTask"
      accept=".xlsx,.xls"
      tip="列：年份 / 部门ID / 科目编码 / 项目编码 / 年度总额 / 1月...12月"
    >
      <template #preview="{ preview }">
        <div v-if="preview" class="preview">
          <div class="meta">
            <span>模板版本：{{ preview.templateVersion || '-' }}</span>
            <span>年份：{{ preview.year ?? '-' }}</span>
            <span>部门：{{ preview.deptId ?? '-' }}</span>
            <span>年度总额：{{ preview.totalAmount ?? '-' }}</span>
            <span>明细行：{{ (preview.lines || []).length }}</span>
          </div>
          <el-table :data="(preview.lines || []).slice(0, 50)" border stripe size="small" max-height="320">
            <el-table-column prop="subjectName" label="科目" min-width="140" />
            <el-table-column prop="projectName" label="项目" min-width="120" />
            <el-table-column label="期间" width="90">
              <template #default="{ row }">{{ enumLabel('period', row.period) }}</template>
            </el-table-column>
            <el-table-column prop="amount" label="金额" width="120" align="right" />
          </el-table>
          <div class="more" v-if="(preview.lines || []).length > 50">仅显示前 50 行……</div>
        </div>
        <el-empty v-else description="无预览内容" :image-size="60" />
      </template>
    </ImportWizard>
  </el-card>
</template>

<script setup>
import ImportWizard from '@/components/ImportWizard.vue'
import { enumLabel } from '@/constants/enums'
import { previewBudgetImport, importBudget, budgetImportTask } from '@/api/budget'

// P-B3 年度预算导入向导
</script>

<style scoped>
.tip-box { margin-bottom: 16px; color: var(--app-text-secondary, #6b7280); font-size: 13px; background: var(--app-bg-table-head, #f7f9fc); border: 1px solid var(--app-border-card, #e7edf5); border-radius: var(--app-radius, 8px); padding: 10px 12px; }
.preview .meta { display: flex; gap: 18px; flex-wrap: wrap; margin-bottom: 10px; color: var(--app-text-secondary, #6b7280); font-size: 13px; }
.more { color: var(--app-text-weak, #7b8797); font-size: 12px; margin-top: 6px; }
</style>
