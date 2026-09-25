<template>
  <div>
    <el-card>
      <PageHead title="已办审批" />
      <div class="filter-row">
        <el-select v-model="queryBizType" placeholder="业务类型" clearable style="width: 160px" @change="resetLoad">
          <el-option v-for="t in bizTypes" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="resetLoad">查询</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column label="业务类型" width="120">
          <template #default="{ row }">
            <StatusTag v-if="row.bizType" :value="row.bizType" enum-key="approvalBizType" />
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="结论" width="90">
          <template #default="{ row }">
            <el-tag :type="row.action === 'APPROVE' ? 'success' : 'danger'" size="small">
              {{ actionLabel(row.action) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="comment" label="审批意见" min-width="160" show-overflow-tooltip />
        <el-table-column prop="approverName" label="审批人" width="110" />
        <el-table-column prop="approvedAt" label="审批时间" width="180" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无审批记录" />
        </template>
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
    <ApprovalDetailDrawer v-model="drawerVisible" :task-id="detailTaskId" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import PageHead from '@/components/PageHead.vue'
import StatusTag from '@/components/StatusTag.vue'
import ApprovalDetailDrawer from './ApprovalDetailDrawer.vue'
import { usePagination } from '@/composables/usePagination'
import { listDoneTasks } from '@/api/approval'
import { APPROVAL_BIZ_TYPES, actionLabel } from '@/constants/approval'

const bizTypes = APPROVAL_BIZ_TYPES
const queryBizType = ref(null)
const rows = ref([])
const drawerVisible = ref(false)
const detailTaskId = ref(null)

const { current, pageSize, total, loading, load, onCurrentChange, reset } = usePagination((p) =>
  listDoneTasks({ bizType: queryBizType.value || undefined, ...p })
)

function reload() {
  return load().then((data) => { rows.value = data })
}
function resetLoad() {
  return reset().then((data) => { rows.value = data })
}
function handlePage(p) {
  onCurrentChange(p).then((data) => { rows.value = data })
}

function openDetail(row) {
  detailTaskId.value = row.taskId
  drawerVisible.value = true
}

onMounted(reload)
</script>

<style scoped>
.filter-row {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
