<template>
  <el-card>
    <div class="toolbar">
      <el-select v-model="supplierId" filterable placeholder="选择供应商" style="width: 260px" @change="loadQuals">
        <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
      </el-select>
      <el-button v-permission="writePerm" type="primary" :icon="Plus" :disabled="!supplierId" @click="openCreate">录入资质</el-button>
      <el-button :icon="Refresh" :disabled="!supplierId" @click="loadQuals">刷新</el-button>
    </div>

    <el-table :data="quals" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="160" />
      <el-table-column prop="type" label="类型" width="120" />
      <el-table-column prop="qualName" label="资质名称" min-width="140" />
      <el-table-column prop="expireAt" label="到期时间" width="170" />
      <el-table-column label="审核状态" width="100">
        <template #default="{ row }"><StatusTag :value="row.status" enum-key="qualStatus" /></template>
      </el-table-column>
      <el-table-column label="有效期" width="110">
        <template #default="{ row }"><StatusTag :value="row.validity" enum-key="qualValidity" /></template>
      </el-table-column>
      <el-table-column prop="rejectReason" label="驳回原因" show-overflow-tooltip />
      <el-table-column prop="reviewedAt" label="审核时间" width="170" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="writePerm" link type="primary" v-if="row.status === 0" @click="onSubmitReview(row)">提交审核</el-button>
          <el-button v-permission="writePerm" link type="warning" v-if="row.status === 2" @click="openResubmit(row)">修改重提</el-button>
          <el-button v-permission="writePerm" link type="primary" v-if="row.status === 0" @click="openEdit(row)">编辑</el-button>
          <el-button link type="info" v-if="row.fileKey" @click="showFile(row)">附件</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 录入 / 重提 -->
    <el-dialog v-model="formVisible" :title="formMode === 'resubmit' ? '修改并重提' : '录入资质'" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="资质类型" required><el-input v-model="form.type" placeholder="如 营业执照" /></el-form-item>
        <el-form-item label="资质名称"><el-input v-model="form.qualName" /></el-form-item>
        <el-form-item label="附件"><FileUpload v-model:value="form.fileKey" biz-type="supplier_qual" /></el-form-item>
        <el-form-item label="到期时间">
          <el-date-picker v-model="form.expireAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="留空=长期有效" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveQual">保存</el-button>
      </template>
    </el-dialog>

    <!-- 审核 -->
    <QualReviewDialog v-model="reviewVisible" :qual="reviewingQual" @confirm="onReviewConfirm" />
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import StatusTag from '@/components/StatusTag.vue'
import FileUpload from '@/components/FileUpload.vue'
import QualReviewDialog from '@/components/supplier/QualReviewDialog.vue'
import { pageSuppliers, listQuals, createQual, updateQual, reviewQual, resubmitQual, qualCallback } from '@/api/supplier'

// P-S4 资质管理：录入 → 提交审核 → 审核（通过/驳回）→ 驳回重提
const writePerm = 'supplier:write'

const suppliers = ref([])
const supplierId = ref(null)
const quals = ref([])
const loading = ref(false)

async function loadSuppliers() {
  try {
    const res = await pageSuppliers({ current: 1, size: 200 })
    suppliers.value = res?.data?.records || []
  } catch (e) {
    suppliers.value = []
  }
}

async function loadQuals() {
  if (!supplierId.value) {
    quals.value = []
    return
  }
  loading.value = true
  try {
    const res = await listQuals(supplierId.value)
    quals.value = res?.data || []
  } catch (e) {
    quals.value = []
  } finally {
    loading.value = false
  }
}

// 录入 / 重提
const formVisible = ref(false)
const formMode = ref('create')
const saving = ref(false)
const editingId = ref(null)
const form = reactive({ type: '', qualName: '', fileKey: '', expireAt: null })

function resetForm() {
  Object.assign(form, { type: '', qualName: '', fileKey: '', expireAt: null })
}

function openCreate() {
  editingId.value = null
  formMode.value = 'create'
  resetForm()
  formVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  formMode.value = 'create'
  Object.assign(form, { type: row.type, qualName: row.qualName, fileKey: row.fileKey || '', expireAt: row.expireAt || null })
  formVisible.value = true
}

function openResubmit(row) {
  editingId.value = row.id
  formMode.value = 'resubmit'
  Object.assign(form, { type: row.type, qualName: row.qualName, fileKey: row.fileKey || '', expireAt: row.expireAt || null })
  formVisible.value = true
}

async function onSaveQual() {
  if (!form.type) {
    ElMessage.warning('资质类型必填')
    return
  }
  saving.value = true
  try {
    const payload = { type: form.type, qualName: form.qualName, fileKey: form.fileKey, expireAt: form.expireAt || null }
    if (formMode.value === 'resubmit' && editingId.value) {
      await resubmitQual(supplierId.value, editingId.value, payload)
      ElMessage.success('已重提，状态回到待审')
    } else if (editingId.value) {
      await updateQual(supplierId.value, editingId.value, payload)
      ElMessage.success('保存成功')
    } else {
      await createQual(supplierId.value, payload)
      ElMessage.success('已录入，状态为待审')
    }
    formVisible.value = false
    loadQuals()
  } catch (e) {
    // 错误由拦截器提示
  } finally {
    saving.value = false
  }
}

// 提交审核 → 打开审核弹窗
const reviewVisible = ref(false)
const reviewingQual = ref(null)
const pendingTaskId = ref(null)

async function onSubmitReview(row) {
  try {
    const res = await reviewQual(supplierId.value, row.id)
    pendingTaskId.value = res?.data
    reviewingQual.value = row
    reviewVisible.value = true
    ElMessage.info('已发起审批（本阶段为本地桩，审核即时生效）')
  } catch (e) {
    // 错误由拦截器提示
  }
}

async function onReviewConfirm({ approved, comment }) {
  try {
    await qualCallback(supplierId.value, {
      taskId: pendingTaskId.value,
      approved,
      comment: comment || ''
    })
    ElMessage.success(approved ? '审核通过' : '已驳回')
    reviewVisible.value = false
    loadQuals()
  } catch (e) {
    // 错误由拦截器提示
  }
}

function showFile(row) {
  ElMessage.info(`附件 fileKey：${row.fileKey}`)
}

onMounted(async () => {
  await loadSuppliers()
  if (suppliers.value.length) {
    supplierId.value = suppliers.value[0].id
    loadQuals()
  }
})
</script>

<style scoped>
.toolbar { margin-bottom: 12px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
</style>
