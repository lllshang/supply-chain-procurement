<template>
  <el-drawer
    :model-value="modelValue"
    title="审批详情"
    size="480px"
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <div v-loading="loading">
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="业务类型">{{ bizTypeLabel(detail.bizType) }}</el-descriptions-item>
          <el-descriptions-item label="标题">{{ detail.title || '—' }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ detail.applicant || '—' }}</el-descriptions-item>
          <el-descriptions-item label="金额摘要">{{ detail.amount ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <StatusTag :value="detail.status" enum-key="approvalStatus" />
          </el-descriptions-item>
        </el-descriptions>

        <div class="section-title">节点链</div>
        <el-timeline>
          <el-timeline-item
            v-for="n in detail.nodes || []"
            :key="n.nodeId"
            :type="timelineType(n)"
            :timestamp="`第 ${n.seq ?? '—'} 节点 · ${nodeStatusLabel(n.status)}`"
          >
            <div class="node-title">{{ n.nodeName || n.nodeCode }}</div>
            <div v-if="n.comment" class="node-comment">意见：{{ n.comment }}</div>
          </el-timeline-item>
        </el-timeline>

        <div class="section-title">审批记录</div>
        <el-table :data="detail.records || []" size="small" stripe>
          <el-table-column label="结论" width="80">
            <template #default="{ row }">{{ actionLabel(row.action) }}</template>
          </el-table-column>
          <el-table-column prop="approverName" label="审批人" width="100" />
          <el-table-column prop="comment" label="意见" min-width="120" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="时间" width="160" />
        </el-table>

        <div class="actions">
          <el-button v-if="sourceRoute" size="small" @click="goSource">查看来源单据</el-button>
          <template v-if="detail.canApprove">
            <el-button size="small" type="success" v-permission="'approval:approve'" @click="onApprove">同意</el-button>
            <el-button size="small" type="danger" v-permission="'approval:approve'" @click="onReject">驳回</el-button>
          </template>
        </div>
      </template>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import StatusTag from '@/components/StatusTag.vue'
import { getTaskDetail, approveTask, rejectTask } from '@/api/approval'
import { bizTypeLabel, bizTypeRoute, nodeStatusLabel, actionLabel } from '@/constants/approval'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  taskId: { type: [String, Number], default: null }
})
const emit = defineEmits(['update:modelValue', 'acted'])

const router = useRouter()
const loading = ref(false)
const detail = ref(null)

const sourceRoute = computed(() =>
  detail.value ? bizTypeRoute(detail.value.bizType, detail.value.bizId) : null
)

watch(() => [props.modelValue, props.taskId], ([visible]) => {
  if (visible && props.taskId) {
    fetchDetail()
  }
})

async function fetchDetail() {
  loading.value = true
  try {
    const res = await getTaskDetail(props.taskId)
    detail.value = res?.data || null
  } catch (e) {
    detail.value = null
  } finally {
    loading.value = false
  }
}

function timelineType(node) {
  if (node.status === 1) return 'success'
  if (node.status === 2) return 'info'
  return 'warning'
}

function goSource() {
  if (sourceRoute.value) {
    emit('update:modelValue', false)
    router.push(sourceRoute.value)
  }
}

async function onApprove() {
  const { value } = await ElMessageBox.prompt('审批意见（可空）', '同意审批')
  await approveTask(props.taskId, value || '')
  ElMessage.success('已同意')
  emit('acted')
  fetchDetail()
}

async function onReject() {
  const { value } = await ElMessageBox.prompt('驳回必须填写审批意见', '驳回审批', {
    inputValidator: (v) => (v && v.trim() ? true : '驳回意见不能为空')
  })
  await rejectTask(props.taskId, value)
  ElMessage.success('已驳回')
  emit('acted')
  fetchDetail()
}
</script>

<style scoped>
.section-title {
  margin: 16px 0 8px;
  font-weight: 600;
}
.node-title {
  font-weight: 500;
}
.node-comment {
  color: #909399;
  font-size: 12px;
}
.actions {
  margin-top: 16px;
  display: flex;
  gap: 8px;
}
</style>
