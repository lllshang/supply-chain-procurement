<template>
  <div class="unit-config">
    <div class="config-toolbar">
      <div class="config-summary">
        <span class="summary-label">单位列表</span>
        <span class="summary-chip">{{ enabledCount }} 个已启用</span>
        <span v-if="units.length - enabledCount > 0" class="summary-chip muted">
          {{ units.length - enabledCount }} 个已停用
        </span>
      </div>
      <el-button type="primary" :icon="Plus" @click="addUnit">新增单位</el-button>
    </div>

    <div v-if="creating" class="create-row">
      <el-input v-model="creating.name" placeholder="如 箱、瓶、件" class="create-name" @keyup.enter="saveCreate" />
      <span class="create-status">启用</span>
      <el-button type="primary" size="small" :loading="saving" @click="saveCreate">保存</el-button>
      <el-button size="small" @click="creating = null">取消</el-button>
    </div>

    <div class="table-shell">
      <el-table :data="pagedUnits" class="config-table" empty-text="暂无单位">
        <el-table-column label="单位名称" min-width="260">
          <template #default="{ row }">
            <el-input v-model="row.name" placeholder="如 箱、瓶、件" @blur="onNameBlur(row)" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="180" align="center">
          <template #default="{ row }">
            <div class="status-control" :class="{ 'is-enabled': row.status === 0 }">
              <el-switch
                :model-value="row.status === 0"
                active-color="#409eff"
                @change="(val) => onToggle(row, val)"
              />
              <span>{{ row.status === 0 ? '已启用' : '已停用' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-button class="table-action danger-action" :icon="Delete" @click="removeUnit(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="totalPages > 1" class="pagination-row">
      <span class="total-text">共 {{ units.length }} 个单位</span>
      <el-pagination
        v-model:current-page="current"
        :page-size="pageSize"
        :total="units.length"
        layout="prev, pager, next"
        background
      />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'
import { listUnits, createUnit, updateUnit, invalidateUnit, enableUnit, deleteUnit } from '@/api/catalog'

const units = ref([])
const current = ref(1)
const pageSize = 10
const creating = ref(null)
const saving = ref(false)

const enabledCount = computed(() => units.value.filter((u) => u.status === 0).length)
const totalPages = computed(() => Math.max(1, Math.ceil(units.value.length / pageSize)))
const pagedUnits = computed(() =>
  units.value.slice((current.value - 1) * pageSize, current.value * pageSize),
)

function genCode() {
  return 'U' + Date.now().toString(36).toUpperCase()
}

async function load() {
  try {
    const res = await listUnits()
    const data = (res && res.data) || []
    units.value = data.map((u) => ({ ...u, _origName: u.name }))
    if (current.value > totalPages.value) current.value = totalPages.value
  } catch (e) {
    units.value = []
  }
}

function addUnit() {
  if (creating.value) return
  creating.value = { name: '' }
}

async function saveCreate() {
  const name = (creating.value.name || '').trim()
  if (!name) {
    ElMessage.warning('单位名称不能为空')
    return
  }
  if (units.value.some((u) => u.name === name)) {
    ElMessage.warning('单位名称不能重复')
    return
  }
  saving.value = true
  try {
    await createUnit({ code: genCode(), name })
    ElMessage.success('单位已新增')
    creating.value = null
    await load()
  } catch (e) {
    // 错误由拦截器提示
  } finally {
    saving.value = false
  }
}

async function onNameBlur(row) {
  const name = (row.name || '').trim()
  if (!name) {
    ElMessage.warning('单位名称不能为空')
    await load()
    return
  }
  if (units.value.some((u) => u.id !== row.id && u.name === name)) {
    ElMessage.warning('单位名称不能重复')
    await load()
    return
  }
  if (row._origName && row._origName === name) return
  try {
    await updateUnit(row.id, { code: row.code, name })
    row._origName = name
  } catch (e) {
    await load()
  }
}

async function onToggle(row, val) {
  try {
    if (val) await enableUnit(row.id)
    else await invalidateUnit(row.id)
  } catch (e) {
    // 被引用时后端拒绝，reload 还原
  }
  await load()
}

async function removeUnit(row) {
  try {
    await ElMessageBox.confirm(`确认删除单位「${row.name || '未命名'}」吗？`, '删除单位', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch (e) {
    return
  }
  try {
    await deleteUnit(row.id)
    ElMessage.success('单位已删除')
    await load()
  } catch (e) {
    // 被引用时后端拒绝
  }
}

onMounted(load)
</script>

<style scoped>
.config-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  max-width: 1180px;
  min-height: 58px;
  margin: 0 auto;
  padding: 10px 16px;
  border: 1px solid #e2e8f0;
  border-bottom: 0;
  border-radius: 8px 8px 0 0;
  background: #fbfdff;
}

.config-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.summary-label {
  margin-right: 4px;
  color: #334155;
  font-size: 14px;
  font-weight: 600;
}

.summary-chip {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 9px;
  border-radius: 999px;
  background: #ecfdf5;
  color: #047857;
  font-size: 12px;
  line-height: 1;
}

.summary-chip.muted {
  background: #f1f5f9;
  color: #64748b;
}

.create-row {
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: 1180px;
  margin: 0 auto;
  padding: 10px 16px;
  border: 1px solid #d6e4ff;
  border-bottom: 0;
  background: #f5f9ff;
}

.create-name {
  width: 260px;
}

.create-status {
  color: #64748b;
  font-size: 13px;
}

.table-shell {
  width: 100%;
  max-width: 1180px;
  margin: 0 auto;
  overflow-x: auto;
  border: 1px solid #e2e8f0;
  border-radius: 0 0 8px 8px;
}

.pagination-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  max-width: 1180px;
  margin: 16px auto 0;
}

.total-text {
  color: #6b7280;
  font-size: 13px;
}

.config-table {
  width: 100%;
  --el-table-border-color: #e8edf3;
  --el-table-header-bg-color: #f8fafc;
  --el-table-row-hover-bg-color: #f6faff;
}

.config-table :deep(th.el-table__cell) {
  height: 42px;
  padding: 0;
  background: #f8fafc;
  color: #475569;
  font-size: 13px;
  font-weight: 600;
}

.config-table :deep(td.el-table__cell) {
  padding: 9px 0;
}

.config-table :deep(.el-input__wrapper) {
  min-height: 32px;
  box-shadow: 0 0 0 1px #dbe3ec inset;
}

.config-table :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #93c5fd inset;
}

.config-table :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #3b82f6 inset;
}

.status-control {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #94a3b8;
  font-size: 13px;
}

.status-control.is-enabled {
  color: #2563eb;
}

.table-action {
  min-width: 72px;
  height: 30px;
  padding: 0 10px;
  border-color: #e2e8f0;
  border-radius: 6px;
  background: #fff;
  color: #475569;
}

.table-action:hover {
  border-color: #fecaca;
  background: #fff7f7;
  color: #dc2626;
}

.danger-action {
  --el-button-hover-text-color: #dc2626;
  --el-button-hover-border-color: #fecaca;
  --el-button-hover-bg-color: #fff7f7;
}
</style>
