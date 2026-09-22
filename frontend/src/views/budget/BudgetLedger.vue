<template>
  <div>
    <el-card>
      <div class="toolbar">
        <el-input-number v-model="year" :min="2000" :max="2100" :controls="false" placeholder="年份" style="width: 130px" />
        <el-button type="primary" :icon="Search" @click="reload">查询</el-button>
      </div>
      <el-table
        :data="headers"
        v-loading="loading"
        border
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
        :current-page="current"
        :page-size="pageSize"
        @current-change="handlePage"
      />
    </el-card>

    <el-card class="ledger-card">
      <div class="ledger-head">
        <span class="title">预算台账</span>
        <span v-if="selectedHeader" class="sub">
          年度 {{ selectedHeader.year }} · 部门 {{ selectedHeader.deptId }} · 总额 {{ selectedHeader.totalAmount }}
        </span>
        <el-checkbox v-model="showProject" class="show-project">展开项目维度</el-checkbox>
      </div>
      <BudgetLedgerTable v-if="lines.length" :lines="lines" :show-project="showProject" v-loading="linesLoading" />
      <el-empty v-else description="请先在上方选择一条预算头查看台账" />
      <div class="note">注：usedAmount 本阶段为 0（P3 写入）。</div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import StatusTag from '@/components/StatusTag.vue'
import BudgetLedgerTable from '@/components/BudgetLedgerTable.vue'
import { pageBudgetHeaders, listBudgetLines } from '@/api/budget'
import { usePagination } from '@/composables/usePagination'

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

onMounted(reload)
</script>

<style scoped>
.toolbar { margin-bottom: 12px; display: flex; gap: 8px; align-items: center; }
.pager { margin-top: 16px; justify-content: flex-end; display: flex; }
.ledger-card { margin-top: 16px; }
.ledger-head { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.title { font-weight: 600; }
.sub { color: #666; font-size: 13px; }
.show-project { margin-left: auto; }
.note { margin-top: 10px; color: #999; font-size: 12px; }
</style>
