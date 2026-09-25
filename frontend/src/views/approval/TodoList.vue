<template>
  <div>
    <el-card>
      <PageHead title="待办审批" />
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane v-for="t in bizTypes" :key="t.value" :label="t.label" :name="t.value" />
      </el-tabs>
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column label="业务类型" width="120">
          <template #default="{ row }"><StatusTag :value="row.bizType" enum-key="approvalBizType" /></template>
        </el-table-column>
        <el-table-column prop="remark" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="applicant" label="申请人" width="110" />
        <el-table-column label="金额摘要" width="130" align="right">
          <template #default="{ row }">{{ row.amount ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="currentNode" label="当前节点" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" enum-key="approvalStatus" /></template>
        </el-table-column>
        <el-table-column prop="createdAt" label="发起时间" width="180" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="isActive(row)"
              link
              type="success"
              v-permission="'approval:approve'"
              @click="onApprove(row)"
            >同意</el-button>
            <el-button
              v-if="isActive(row)"
              link
              type="danger"
              v-permission="'approval:approve'"
              @click="onReject(row)"
            >驳回</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无待办审批" />
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
    <ApprovalDetailDrawer v-model="drawerVisible" :task-id="detailTaskId" @acted="reload" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '@/components/PageHead.vue'
import StatusTag from '@/components/StatusTag.vue'
import ApprovalDetailDrawer from './ApprovalDetailDrawer.vue'
import { usePagination } from '@/composables/usePagination'
import { listTodoTasks, approveTask, rejectTask } from '@/api/approval'
import { APPROVAL_BIZ_TYPES } from '@/constants/approval'

const bizTypes = APPROVAL_BIZ_TYPES
const activeTab = ref('all')
const rows = ref([])
const drawerVisible = ref(false)
const detailTaskId = ref(null)

const { current, pageSize, total, loading, load, onCurrentChange } = usePagination((p) =>
  listTodoTasks({ bizType: activeTab.value === 'all' ? undefined : activeTab.value, ...p })
)

function isActive(row) {
  return ['CREATED', 'IN_PROGRESS'].includes(row.status)
}

function reload() {
  return load().then((data) => { rows.value = data })
}
function onTabChange() {
  current.value = 1
  reload()
}
function handlePage(p) {
  onCurrentChange(p).then((data) => { rows.value = data })
}

function openDetail(row) {
  // ID 全程字符串零 Number() 转换（#28/#1 契约）
  detailTaskId.value = row.id
  drawerVisible.value = true
}

async function onApprove(row) {
  const { value } = await ElMessageBox.prompt('审批意见（可空）', '同意审批')
  await approveTask(row.id, value || '')
  ElMessage.success('已同意')
  reload()
}

async function onReject(row) {
  // 驳回意见前端必填（后端网关内第二道拦截，双拦截 AC②）
  const { value } = await ElMessageBox.prompt('驳回必须填写审批意见', '驳回审批', {
    inputValidator: (v) => (v && v.trim() ? true : '驳回意见不能为空')
  })
  await rejectTask(row.id, value)
  ElMessage.success('已驳回')
  reload()
}

onMounted(reload)
</script>

<style scoped>
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
