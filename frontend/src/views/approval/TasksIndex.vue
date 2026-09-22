<template>
  <div>
    <el-card>
      <PageHead title="待我审批" />
      <div class="filter-row">
        <EnumSelect v-model="queryBizType" enum-key="approvalBizType" placeholder="业务类型" style="--filter-width: 160px" />
        <EnumSelect v-model="queryStatus" enum-key="approvalStatus" placeholder="任务状态" style="--filter-width: 140px" />
        <el-button type="primary" :icon="Search" @click="reload">查询</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="id" label="任务 ID" width="200" />
        <el-table-column label="业务类型" width="110">
          <template #default="{ row }"><StatusTag :value="row.bizType" enum-key="approvalBizType" /></template>
        </el-table-column>
        <el-table-column prop="bizId" label="业务单据" width="200" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="currentNode" label="当前节点" width="140" />
        <el-table-column prop="applicant" label="申请人" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" enum-key="approvalStatus" /></template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="['CREATED', 'IN_PROGRESS'].includes(row.status)" link type="success" v-permission="'approval:approve'" @click="onApprove(row)">同意</el-button>
            <el-button v-if="['CREATED', 'IN_PROGRESS'].includes(row.status)" link type="danger" v-permission="'approval:approve'" @click="onReject(row)">驳回</el-button>
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
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import PageHead from '@/components/PageHead.vue'
import StatusTag from '@/components/StatusTag.vue'
import EnumSelect from '@/components/EnumSelect.vue'
import { usePagination } from '@/composables/usePagination'
import { searchApprovalTasks, approveTask, rejectTask } from '@/api/purchase2'

const queryBizType = ref(null)
const queryStatus = ref('CREATED')
const rows = ref([])
const { current, pageSize, total, loading, load, onCurrentChange } = usePagination((p) =>
  searchApprovalTasks({ bizType: queryBizType.value || undefined, status: queryStatus.value || undefined, ...p })
)

function reload() {
  return load().then((data) => { rows.value = data })
}
function handlePage(p) {
  onCurrentChange(p).then((data) => { rows.value = data })
}

async function onApprove(row) {
  const { value } = await ElMessageBox.prompt('审批意见（可空）', '同意审批')
  await approveTask(row.id, value || '')
  ElMessage.success('已同意（幂等：重复操作任务状态不变）')
  reload()
}

async function onReject(row) {
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
.filter-row {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}
</style>
