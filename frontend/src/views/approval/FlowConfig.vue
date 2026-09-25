<template>
  <div>
    <el-card>
      <PageHead title="流程配置" />
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="配置变更仅影响新创建任务；在途任务按提交时节点快照走完（拍板清单声明）"
      />
      <el-table :data="rows" v-loading="loading" stripe row-key="flowKey">
        <el-table-column type="expand">
          <template #default="{ row }">
            <el-table :data="row.nodes" size="small" border>
              <el-table-column prop="nodeCode" label="节点编码" width="90" />
              <el-table-column prop="nodeName" label="节点名称" min-width="120" />
              <el-table-column prop="seq" label="序" width="60" />
              <el-table-column label="审批人规则" min-width="180">
                <template #default="{ row: n }">
                  {{ approverRuleText(n) }}
                </template>
              </el-table-column>
              <el-table-column label="金额区间" width="170">
                <template #default="{ row: n }">
                  {{ amountRangeText(n) }}
                </template>
              </el-table-column>
              <el-table-column prop="signType" label="签类型" width="80" />
              <el-table-column label="启用" width="70">
                <template #default="{ row: n }">{{ n.enabled === 1 ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column label="操作" width="90">
                <template #default="{ row: n }">
                  <el-button link type="primary" v-permission="'approval:config'" @click="openNodeEditor(row, n)">编辑</el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>
        </el-table-column>
        <el-table-column prop="flowKey" label="流程键" width="180" />
        <el-table-column prop="flowDef.flowName" label="流程名称" min-width="140" />
        <el-table-column prop="flowDef.flowVersion" label="版本" width="70" />
        <el-table-column label="启用" width="90">
          <template #default="{ row }">
            <el-switch
              :model-value="row.flowDef.enabled === 1"
              v-permission="'approval:config'"
              @change="(v) => toggleFlow(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="flowDef.remark" label="备注" min-width="180" show-overflow-tooltip />
      </el-table>
    </el-card>

    <el-dialog v-model="editorVisible" title="编辑节点" width="480px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="节点名称">{{ form.nodeName }}</el-form-item>
        <el-form-item label="审批人类型">
          <el-select v-model="form.approverType" style="width: 100%">
            <el-option label="角色（ROLE）" value="ROLE" />
            <el-option label="申请人部门负责人（DEPT_HEAD_OF_APPLICANT）" value="DEPT_HEAD_OF_APPLICANT" />
            <el-option label="指定用户（USER，兜底慎用）" value="USER" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.approverType === 'ROLE'" label="角色编码">
          <el-select v-model="form.approverValue" style="width: 100%" clearable>
            <el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.approverType === 'USER'" label="用户名">
          <el-input v-model="form.approverValue" placeholder="指定用户名（兜底慎用）" />
        </el-form-item>
        <el-form-item label="金额下界（含）">
          <el-input-number v-model="form.amountMin" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="金额上界（不含）">
          <el-input-number v-model="form.amountMax" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="签类型">
          <el-select v-model="form.signType" style="width: 100%">
            <el-option label="或签 ANY（任一人审批即过）" value="ANY" />
            <el-option label="会签 ALL（全部候选审批）" value="ALL" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveNode">保存（仅影响新任务）</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '@/components/PageHead.vue'
import { listFlows, updateFlow, updateFlowNode } from '@/api/approval'
import { bizTypeLabel } from '@/constants/approval'

const rows = ref([])
const loading = ref(false)
const editorVisible = ref(false)
const saving = ref(false)
const editing = ref(null)
const form = ref({})

// 审批角色选项（对齐 p4 种子 5 角色 + SUPER_ADMIN 兜底展示）
const roleOptions = [
  { value: 'DEPT_HEAD', label: '部门负责人' },
  { value: 'PURCHASE_DEPT', label: '采购部（招采）' },
  { value: 'FINANCE', label: '财务负责人' },
  { value: 'PROCUREMENT_LEAD', label: '采购负责人' },
  { value: 'LEADER', label: '分管领导' }
]

async function reload() {
  loading.value = true
  try {
    const res = await listFlows()
    rows.value = res?.data || []
  } finally {
    loading.value = false
  }
}

function approverRuleText(node) {
  if (node.approverType === 'ROLE') return `角色：${node.approverValue || '—'}`
  if (node.approverType === 'DEPT_HEAD_OF_APPLICANT') return '申请人部门负责人（逐级上溯）'
  if (node.approverType === 'USER') return `指定用户：${node.approverValue || '—'}`
  return node.approverType || '—'
}

function amountRangeText(node) {
  const min = node.amountMin ?? '∞负'
  const max = node.amountMax ?? '∞'
  if (node.amountMin == null && node.amountMax == null) return '恒生效'
  return `[${node.amountMin ?? '0'}, ${max})`
}

function openNodeEditor(flow, node) {
  editing.value = { flowKey: flow.flowKey, nodeId: node.id }
  form.value = {
    nodeName: node.nodeName,
    approverType: node.approverType,
    approverValue: node.approverValue,
    amountMin: node.amountMin == null ? null : Number(node.amountMin),
    amountMax: node.amountMax == null ? null : Number(node.amountMax),
    signType: node.signType || 'ANY',
    enabled: node.enabled
  }
  editorVisible.value = true
}

async function saveNode() {
  saving.value = true
  try {
    await updateFlowNode(editing.value.flowKey, editing.value.nodeId, {
      approverType: form.value.approverType,
      approverValue: form.value.approverType === 'DEPT_HEAD_OF_APPLICANT' ? null : form.value.approverValue,
      amountMin: form.value.amountMin,
      amountMax: form.value.amountMax,
      signType: form.value.signType,
      enabled: form.value.enabled
    })
    ElMessage.success('已保存（配置缓存已失效，保存即生效；仅影响新任务）')
    editorVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

async function toggleFlow(flow, enabled) {
  await ElMessageBox.confirm(
    `确认${enabled ? '启用' : '停用'}流程「${flow.flowDef.flowName || bizTypeLabel(flow.flowKey)}」？停用后该类型新任务拒绝创建，在途任务不受影响。`,
    '流程启停'
  )
  await updateFlow(flow.flowKey, { enabled: enabled ? 1 : 0 })
  ElMessage.success('已保存')
  reload()
}

onMounted(reload)
</script>
