<template>
  <div>
    <el-card>
      <PageHead title="预算台账" />
      <div class="toolbar">
        <el-input-number v-model="year" :min="2000" :max="2100" :controls="false" placeholder="年份" style="width: 130px" />
        <el-button type="primary" :icon="Search" @click="reload">查询</el-button>
      </div>
      <el-table
        :data="headers"
        v-loading="loading"
        stripe
        highlight-current-row
        @current-change="onSelectHeader"
      >
        <el-table-column prop="id" label="ID" width="160" />
        <el-table-column prop="year" label="年度" width="100" />
        <el-table-column prop="deptId" label="部门ID" width="140" />
        <el-table-column prop="totalAmount" label="年度总额" width="160" align="right" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" enum-key="budgetHeaderStatus" /></template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="loadLines(row)">查看台账</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        background
        layout="total, prev, pager, next"
        :total="total"
        v-model:current-page="current"
        :page-size="pageSize"
        @current-change="handlePage"
      />
    </el-card>

    <el-card class="ledger-card">
      <div class="ledger-head">
        <span class="title">台账明细</span>
        <span v-if="selectedHeader" class="sub">
          年度 {{ selectedHeader.year }} · 部门 {{ selectedHeader.deptId }} · 总额 {{ selectedHeader.totalAmount }}
        </span>
        <el-checkbox v-model="showProject" class="show-project">展开项目维度</el-checkbox>
        <el-button type="primary" link :icon="EditPen" :disabled="!lines.length" @click="openAdjust">月度调整</el-button>
      </div>
      <BudgetLedgerTable v-if="lines.length" :lines="lines" :show-project="showProject" v-loading="linesLoading" />
      <el-empty v-else description="请先在上方选择一条预算头查看台账" />
      <div class="note">注：usedAmount 由 P3 预算占用/核销流水写入；月度调整超 20% 自动转 BUDGET 审批（R8）。</div>
    </el-card>

    <!-- 月度调整（P3 §2.3：财务发起，审批后更新台账；调减校验 amount≥used） -->
    <el-dialog v-model="adjustVisible" title="月度预算调整" width="520px" destroy-on-close>
      <el-form :model="adjustForm" label-width="100px">
        <el-form-item label="调整行" required>
          <el-select v-model="adjustForm.lineId" placeholder="科目 × 月份" style="width: 100%" @change="onPickLine">
            <el-option
              v-for="l in lines"
              :key="l.id"
              :label="`${l.subjectName} · ${periodLabel(l.period)} · 当前 ${l.amount}`"
              :value="l.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="当前额度">
          <span>{{ pickedLine ? pickedLine.amount : '-' }}</span>
        </el-form-item>
        <el-form-item label="新额度" required>
          <el-input-number v-model="adjustForm.newAmount" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="调整原因" required>
          <el-input v-model="adjustForm.reason" type="textarea" :rows="2" placeholder="审批留痕必填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" :loading="adjusting" @click="onAdjust">提交调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, EditPen } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import BudgetLedgerTable from '@/components/BudgetLedgerTable.vue'
import PageHead from '@/components/PageHead.vue'
import { pageBudgetHeaders, listBudgetLines, adjustBudgetLine } from '@/api/budget'
import { usePagination } from '@/composables/usePagination'
import { PERIOD_OPTIONS } from '@/constants/enums'

// P-B4 预算台账：预算头列表 + 12 月台账
const year = ref(new Date().getFullYear())
const headers = ref([])
const { current, pageSize, total, loading, load, onCurrentChange } = usePagination((p) =>
  pageBudgetHeaders({ year: year.value, ...p })
)

const selectedHeader = ref(null)
const lines = ref([])
const linesLoading = ref(false)
const showProject = ref(false)

function reload() {
  return load().then((rows) => {
    headers.value = rows
  })
}
function handlePage(p) {
  onCurrentChange(p).then((rows) => {
    headers.value = rows
  })
}

function onSelectHeader(row) {
  if (row) loadLines(row)
}

async function loadLines(row) {
  selectedHeader.value = row
  linesLoading.value = true
  try {
    const res = await listBudgetLines(row.id)
    lines.value = res?.data || []
    showProject.value = lines.value.some((l) => l.projectId != null)
  } catch (e) {
    lines.value = []
  } finally {
    linesLoading.value = false
  }
}

// ---- 月度调整（P3：财务发起；调增直生效，调减校验 amount≥used，超20% 转 BUDGET 审批） ----
const adjustVisible = ref(false)
const adjusting = ref(false)
const adjustForm = reactive({ lineId: null, newAmount: null, reason: '' })
const pickedLine = ref(null)

function periodLabel(p) {
  const hit = PERIOD_OPTIONS.find((o) => o.value === p)
  return hit ? hit.label : `${p}月`
}

function openAdjust() {
  Object.assign(adjustForm, { lineId: null, newAmount: null, reason: '' })
  pickedLine.value = null
  adjustVisible.value = true
}

function onPickLine(lineId) {
  pickedLine.value = lines.value.find((l) => l.id === lineId) || null
  if (pickedLine.value) adjustForm.newAmount = Number(pickedLine.value.amount) || 0
}

async function onAdjust() {
  if (!adjustForm.lineId || adjustForm.newAmount == null || !adjustForm.reason.trim()) {
    ElMessage.warning('请选择调整行、填写新额度与调整原因')
    return
  }
  adjusting.value = true
  try {
    const res = await adjustBudgetLine(adjustForm.lineId, {
      newAmount: adjustForm.newAmount,
      reason: adjustForm.reason.trim()
    })
    // 返回 pending=true 表示超 20% 已转 BUDGET 升级审批，台账待审批后更新
    if (res.data === true) {
      ElMessage.success('调整已生效，台账已更新')
    } else {
      ElMessage.warning('调整幅度超 20%，已转预算升级审批，台账待审批通过后更新')
    }
    adjustVisible.value = false
    if (selectedHeader.value) loadLines(selectedHeader.value)
  } finally {
    adjusting.value = false
  }
}

onMounted(reload)
</script>

<style scoped>
.toolbar { margin-bottom: 12px; display: flex; gap: 8px; align-items: center; }
.pager { margin-top: 16px; justify-content: flex-end; display: flex; }
.ledger-card { margin-top: 16px; }
.ledger-head { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.title { font-weight: 600; }
.sub { color: var(--app-text-secondary, #6b7280); font-size: 13px; }
.show-project { margin-left: auto; }
.note { margin-top: 10px; color: var(--app-text-weak, #7b8797); font-size: 12px; }
</style>
