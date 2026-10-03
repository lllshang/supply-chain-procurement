<template>
  <div class="spec-config">
    <div class="config-toolbar">
      <div class="config-summary">
        <span class="summary-label">规格类型列表</span>
        <span class="summary-chip">{{ enabledCount }} 个已启用</span>
        <span v-if="typeRows.length - enabledCount > 0" class="summary-chip muted">
          {{ typeRows.length - enabledCount }} 个已停用
        </span>
      </div>
      <el-button type="primary" :icon="Plus" @click="addType">新增规格类型</el-button>
    </div>

    <div v-if="creating" class="create-row">
      <el-input v-model="creating.specName" placeholder="如 大小、颜色、容量" class="create-name" />
      <el-select
        v-model="creating.values"
        multiple
        filterable
        allow-create
        default-first-option
        placeholder="输入参数值后回车"
        class="create-values"
      />
      <el-button type="primary" size="small" :loading="saving" @click="saveCreate">保存</el-button>
      <el-button size="small" @click="creating = null">取消</el-button>
    </div>

    <div class="table-shell">
      <el-table :data="pagedTypes" class="config-table specification-table" empty-text="暂无规格类型">
        <el-table-column label="规格类型" width="220">
          <template #default="{ row }">
            <el-input v-model="row.specName" placeholder="如 大小、颜色、容量" @blur="onNameBlur(row)" />
          </template>
        </el-table-column>
        <el-table-column label="参数值" min-width="360">
          <template #default="{ row }">
            <el-select
              :model-value="row.values.map((v) => v.specValue)"
              multiple
              filterable
              allow-create
              default-first-option
              placeholder="输入参数值后回车"
              class="full-width"
              @change="(vals) => onValuesChange(row, vals)"
            />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="180" align="center">
          <template #default="{ row }">
            <div class="status-control" :class="{ 'is-enabled': row.enabled }">
              <el-switch
                :model-value="row.enabled"
                active-color="#409eff"
                @change="(val) => onToggle(row, val)"
              />
              <span>{{ row.enabled ? '已启用' : '已停用' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-button class="table-action danger-action" :icon="Delete" @click="removeType(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="totalPages > 1" class="pagination-row">
      <span class="total-text">共 {{ typeRows.length }} 个规格类型</span>
      <el-pagination
        v-model:current-page="current"
        :page-size="pageSize"
        :total="typeRows.length"
        layout="prev, pager, next"
        background
        hide-on-single-page
      />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'
import {
  listSpecOptions,
  createSpecOption,
  updateSpecOption,
  invalidateSpecOption,
  enableSpecOption,
} from '@/api/catalog'

const specs = ref([])
const typeRows = ref([])
const current = ref(1)
const pageSize = 10
const creating = ref(null)
const saving = ref(false)

const enabledCount = computed(() => typeRows.value.filter((t) => t.enabled).length)
const totalPages = computed(() => Math.max(1, Math.ceil(typeRows.value.length / pageSize)))
const pagedTypes = computed(() =>
  typeRows.value.slice((current.value - 1) * pageSize, current.value * pageSize),
)

function rebuild() {
  const map = new Map()
  for (const o of specs.value) {
    if (!map.has(o.specName)) map.set(o.specName, [])
    map.get(o.specName).push(o)
  }
  typeRows.value = [...map.entries()].map(([name, opts]) => ({
    specName: name,
    _origName: name,
    values: opts.map((o) => ({ id: o.id, specValue: o.specValue, status: o.status })),
    enabled: opts.some((o) => o.status === 0),
  }))
}

async function load() {
  try {
    const res = await listSpecOptions()
    specs.value = (res && res.data) || []
    rebuild()
    if (current.value > totalPages.value) current.value = totalPages.value
  } catch (e) {
    specs.value = []
    typeRows.value = []
  }
}

function addType() {
  if (creating.value) return
  creating.value = { specName: '', values: [] }
}

async function saveCreate() {
  const name = (creating.value.specName || '').trim()
  if (!name) {
    ElMessage.warning('规格类型名称不能为空')
    return
  }
  if (typeRows.value.some((t) => t.specName === name)) {
    ElMessage.warning('规格类型名称不能重复')
    return
  }
  const vals = creating.value.values || []
  if (!vals.length) {
    ElMessage.warning('请至少添加一个参数值')
    return
  }
  saving.value = true
  try {
    for (const v of vals) {
      await createSpecOption({ specName: name, specValue: v })
    }
    ElMessage.success('规格类型已新增')
    creating.value = null
    await load()
  } catch (e) {
    // 错误由拦截器提示
  } finally {
    saving.value = false
  }
}

async function onNameBlur(type) {
  const name = (type.specName || '').trim()
  if (!name) {
    ElMessage.warning('规格类型名称不能为空')
    await load()
    return
  }
  if (typeRows.value.some((t) => t !== type && t.specName === name)) {
    ElMessage.warning('规格类型名称不能重复')
    await load()
    return
  }
  if (type._origName && type._origName === name) return
  try {
    for (const v of type.values) {
      await updateSpecOption(v.id, { specName: name, specValue: v.specValue })
    }
    type._origName = name
    await load()
  } catch (e) {
    await load()
  }
}

async function onValuesChange(type, newValues) {
  const currentVals = type.values.map((v) => v.specValue)
  const added = newValues.filter((v) => !currentVals.includes(v))
  const removed = type.values.filter((v) => !newValues.includes(v.specValue))
  try {
    for (const v of removed) {
      await invalidateSpecOption(v.id)
    }
    for (const v of added) {
      await createSpecOption({ specName: type.specName, specValue: v })
    }
    await load()
  } catch (e) {
    await load()
  }
}

async function onToggle(type, val) {
  try {
    for (const v of type.values) {
      if (val && v.status !== 0) await enableSpecOption(v.id)
      if (!val && v.status === 0) await invalidateSpecOption(v.id)
    }
    await load()
  } catch (e) {
    await load()
  }
}

async function removeType(type) {
  try {
    await ElMessageBox.confirm(`确认删除规格类型「${type.specName || '未命名'}」吗？`, '删除规格类型', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch (e) {
    return
  }
  try {
    for (const v of type.values) {
      await invalidateSpecOption(v.id)
    }
    ElMessage.success('规格类型已删除')
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
  width: 200px;
}

.create-values {
  flex: 1;
  max-width: 460px;
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

.specification-table {
  min-width: 820px;
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

.config-table :deep(.el-input__wrapper),
.config-table :deep(.el-select__wrapper) {
  min-height: 32px;
  box-shadow: 0 0 0 1px #dbe3ec inset;
}

.config-table :deep(.el-input__wrapper:hover),
.config-table :deep(.el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px #93c5fd inset;
}

.config-table :deep(.el-input__wrapper.is-focus),
.config-table :deep(.el-select__wrapper.is-focused) {
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

.full-width {
  width: 100%;
}
</style>
