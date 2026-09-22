<template>
  <el-card>
    <div class="toolbar">
      <el-button v-permission="writePerm" type="primary" :icon="Plus" @click="openDialog()">新建单位</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="180" />
      <el-table-column prop="code" label="编码" />
      <el-table-column prop="name" label="名称" />
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

    <el-pagination
      class="pager"
      background
      layout="total, prev, pager, next"
      :total="total"
      :current-page="current"
      :page-size="pageSize"
      @current-change="handlePage"
    />

    <el-dialog v-model="visible" :title="form.id ? '编辑单位' : '新建单位'" width="420px">
      <el-form :model="form" label-width="70px">
        <el-form-item label="编码" required>
          <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 PCS/BOX/KG" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="如 个/箱/千克" />
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
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import StatusTag from '@/components/StatusTag.vue'
import { pageUnits, createUnit, updateUnit, invalidateUnit } from '@/api/catalog'
import { usePagination } from '@/composables/usePagination'

// P-C2 单位配置：列表 + 增改/置无效
const writePerm = 'catalog:unit:write'

const list = ref([])
const { current, pageSize, total, loading, load, onCurrentChange } = usePagination(pageUnits)

const visible = ref(false)
const saving = ref(false)
const form = reactive({ id: null, code: '', name: '' })

async function reload() {
  list.value = await load()
}
async function handlePage(p) {
  list.value = await onCurrentChange(p)
}

function openDialog(row) {
  if (row) {
    Object.assign(form, { id: row.id, code: row.code, name: row.name })
  } else {
    Object.assign(form, { id: null, code: '', name: '' })
  }
  visible.value = true
}

async function onSave() {
  if (!form.code || !form.name) {
    ElMessage.warning('编码与名称必填')
    return
  }
  saving.value = true
  try {
    const payload = { code: form.code, name: form.name }
    if (form.id) {
      await updateUnit(form.id, payload)
    } else {
      await createUnit(payload)
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
    await ElMessageBox.confirm(`确认将「${row.name}」置为无效？`, '提示', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await invalidateUnit(row.id)
    ElMessage.success('已置为无效')
    reload()
  } catch (e) {
    // 被引用时后端拒绝
  }
}

onMounted(reload)
</script>

<style scoped>
.toolbar { margin-bottom: 12px; }
.pager { margin-top: 16px; justify-content: flex-end; display: flex; }
</style>
