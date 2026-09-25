<template>
  <div>
    <el-card>
      <PageHead title="合同类型配置" />
      <div class="filter-row">
        <el-button type="primary" :icon="Plus" v-permission="'contract:type:write'" @click="openCreate">新增类型</el-button>
        <span class="hint">被合同引用的类型不可删除（可停用）；停用后新建合同不可选，存量不受影响</span>
      </div>
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="typeCode" label="类型编码" width="160" />
        <el-table-column prop="typeName" label="类型名称" min-width="140" />
        <el-table-column label="启用" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-permission="'contract:type:write'" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" v-permission="'contract:type:write'" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无合同类型" />
        </template>
      </el-table>
    </el-card>

    <el-dialog v-model="editorVisible" :title="editingId ? '编辑类型' : '新增类型'" width="420px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="类型编码">
          <el-input v-model="form.typeCode" :disabled="!!editingId" placeholder="如 FRAME" />
        </el-form-item>
        <el-form-item label="类型名称">
          <el-input v-model="form.typeName" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import PageHead from '@/components/PageHead.vue'
import {
  listContractTypes,
  createContractType,
  updateContractType,
  deleteContractType
} from '@/api/purchase2'

const rows = ref([])
const loading = ref(false)
const editorVisible = ref(false)
const saving = ref(false)
const editingId = ref(null)
const form = ref({ typeCode: '', typeName: '', enabled: 1, remark: '' })

async function reload() {
  loading.value = true
  try {
    const res = await listContractTypes()
    rows.value = res?.data || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.value = { typeCode: '', typeName: '', enabled: 1, remark: '' }
  editorVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.value = {
    typeCode: row.typeCode,
    typeName: row.typeName,
    enabled: row.enabled,
    remark: row.remark
  }
  editorVisible.value = true
}

async function save() {
  saving.value = true
  try {
    if (editingId.value) {
      await updateContractType(editingId.value, form.value)
    } else {
      await createContractType(form.value)
    }
    ElMessage.success('已保存')
    editorVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

async function onDelete(row) {
  await ElMessageBox.confirm(
    `确认删除类型「${row.typeName}」？被合同引用的类型将被拒绝删除（可改为停用）。`,
    '删除类型'
  )
  await deleteContractType(row.id)
  ElMessage.success('已删除')
  reload()
}

onMounted(reload)
</script>

<style scoped>
.filter-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.hint {
  color: #909399;
  font-size: 12px;
}
</style>
