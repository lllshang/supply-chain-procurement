<template>
  <el-card>
    <div class="toolbar">
      <el-input-number v-model="year" :min="2000" :max="2100" :controls="false" placeholder="年份" style="width: 130px" />
      <el-button type="primary" :icon="Search" @click="load">查询</el-button>
      <div class="spacer" />
      <el-button v-permission="writePerm" type="primary" :icon="Plus" @click="openForm()">新建项目</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="160" />
      <el-table-column prop="code" label="项目编码" width="160" />
      <el-table-column prop="name" label="项目名称" min-width="160" />
      <el-table-column prop="year" label="年度" width="100" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }"><StatusTag :value="row.status" enum-key="productStatus" /></template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button v-permission="writePerm" link type="primary" @click="openForm(row)">编辑</el-button>
          <el-button v-permission="writePerm" link type="danger" :disabled="row.status === 1" @click="onInvalidate(row)">置无效</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '编辑预算项目' : '新建预算项目'" width="440px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="项目编码" required><el-input v-model="form.code" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="项目名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="年度" required>
          <el-input-number v-model="form.year" :min="2000" :max="2100" :controls="false" style="width: 100%" />
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
import { onMounted, reactive, ref } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatusTag from '@/components/StatusTag.vue'
import { listBudgetProjects, createBudgetProject, updateBudgetProject, invalidateBudgetProject } from '@/api/budget'

// P-B2 预算项目：列表 + 按年筛选 + 增改/置无效
const writePerm = 'budget:project:write'

const year = ref(new Date().getFullYear())
const list = ref([])
const loading = ref(false)

const visible = ref(false)
const saving = ref(false)
const form = reactive({ id: null, code: '', name: '', year: year.value })

async function load() {
  loading.value = true
  try {
    const res = await listBudgetProjects({ year: year.value })
    list.value = res?.data || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

function openForm(row) {
  if (row) {
    Object.assign(form, { id: row.id, code: row.code, name: row.name, year: row.year })
  } else {
    Object.assign(form, { id: null, code: '', name: '', year: year.value })
  }
  visible.value = true
}

async function onSave() {
  if (!form.code || !form.name || !form.year) {
    ElMessage.warning('编码、名称、年度必填')
    return
  }
  saving.value = true
  try {
    const payload = { code: form.code, name: form.name, year: form.year }
    if (form.id) {
      await updateBudgetProject(form.id, payload)
    } else {
      await createBudgetProject(payload)
    }
    ElMessage.success('保存成功')
    visible.value = false
    load()
  } catch (e) {
    // 错误由拦截器提示
  } finally {
    saving.value = false
  }
}

async function onInvalidate(row) {
  try {
    await ElMessageBox.confirm(`确认将「${row.name}」置为无效？`, '提示', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await invalidateBudgetProject(row.id)
    ElMessage.success('已置为无效')
    load()
  } catch (e) {
    // 被引用时后端拒绝
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar { margin-bottom: 12px; display: flex; gap: 8px; align-items: center; }
.spacer { flex: 1; }
</style>
