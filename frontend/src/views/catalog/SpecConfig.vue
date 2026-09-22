<template>
  <el-card>
    <div class="toolbar">
      <el-radio-group v-model="view">
        <el-radio-button value="list">列表</el-radio-button>
        <el-radio-button value="grouped">分组视图</el-radio-button>
      </el-radio-group>
      <el-input
        v-model="keyword"
        class="filter"
        placeholder="按规格名筛选"
        clearable
        :prefix-icon="Search"
      />
      <el-button v-permission="writePerm" type="primary" :icon="Plus" @click="openDialog()">新建规格值</el-button>
    </div>

    <!-- 列表视图 -->
    <el-table v-if="view === 'list'" :data="filteredList" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="180" />
      <el-table-column prop="specName" label="规格名" />
      <el-table-column prop="specValue" label="规格值" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" enum-key="productStatus" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button v-permission="writePerm" link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button
            v-permission="writePerm"
            link
            type="danger"
            :disabled="row.status === 1"
            @click="onInvalidate(row)"
          >置无效</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分组视图 -->
    <div v-else v-loading="loading" class="grouped">
      <el-card v-for="(values, name) in grouped" :key="name" shadow="never" class="group-card">
        <div class="group-title">{{ name }}</div>
        <div class="group-values">
          <el-tag v-for="v in values" :key="v" class="group-tag">{{ v }}</el-tag>
        </div>
      </el-card>
      <el-empty v-if="!Object.keys(grouped).length" description="暂无数据" />
    </div>

    <el-dialog v-model="visible" :title="form.id ? '编辑规格值' : '新建规格值'" width="420px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="规格名" required>
          <el-input v-model="form.specName" :disabled="!!form.id" placeholder="如 颜色/尺寸" />
        </el-form-item>
        <el-form-item label="规格值" required>
          <el-input v-model="form.specValue" placeholder="如 红/S" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatusTag from '@/components/StatusTag.vue'
import { listSpecOptions, listSpecGrouped, createSpecOption, updateSpecOption, invalidateSpecOption } from '@/api/catalog'

// P-C3 规格配置：列表 + 分组视图 + 增改/置无效
const writePerm = 'catalog:spec:write'

const view = ref('list')
const keyword = ref('')
const list = ref([])
const grouped = ref({})
const loading = ref(false)

const filteredList = computed(() => {
  if (!keyword.value) return list.value
  return list.value.filter((r) => (r.specName || '').includes(keyword.value))
})

const visible = ref(false)
const saving = ref(false)
const form = reactive({ id: null, specName: '', specValue: '' })

async function reload() {
  loading.value = true
  try {
    const res = await listSpecOptions()
    list.value = res?.data || []
    if (view.value === 'grouped') {
      const g = await listSpecGrouped()
      grouped.value = g?.data || {}
    }
  } catch (e) {
    list.value = []
    grouped.value = {}
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  if (row) {
    Object.assign(form, { id: row.id, specName: row.specName, specValue: row.specValue })
  } else {
    Object.assign(form, { id: null, specName: '', specValue: '' })
  }
  visible.value = true
}

async function onSave() {
  if (!form.specName || !form.specValue) {
    ElMessage.warning('规格名与规格值必填')
    return
  }
  saving.value = true
  try {
    const payload = { specName: form.specName, specValue: form.specValue }
    if (form.id) {
      await updateSpecOption(form.id, payload)
    } else {
      await createSpecOption(payload)
    }
    ElMessage.success('保存成功')
    visible.value = false
    reload()
  } catch (e) {
    // 错误由拦截器提示
  } finally {
    saving.value = false
  }
}

async function onInvalidate(row) {
  try {
    await ElMessageBox.confirm(`确认将「${row.specName}-${row.specValue}」置为无效？`, '提示', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await invalidateSpecOption(row.id)
    ElMessage.success('已置为无效')
    reload()
  } catch (e) {
    // 被引用时后端拒绝
  }
}

watch(view, () => reload())

onMounted(reload)
</script>

<style scoped>
.toolbar { margin-bottom: 12px; display: flex; gap: 12px; align-items: center; }
.filter { width: 220px; --filter-width: 220px; }
.grouped { display: flex; flex-wrap: wrap; gap: 12px; }
.group-card { min-width: 240px; border-color: var(--app-border-card, #e7edf5); border-radius: var(--app-radius, 8px); }
.group-card :deep(.el-card__body) { padding: 14px 16px; }
.group-title { color: var(--app-text-primary, #1f2937); font-weight: 600; margin-bottom: 8px; }
.group-values { display: flex; flex-wrap: wrap; gap: 6px; }
.group-tag { margin: 0; }
</style>
