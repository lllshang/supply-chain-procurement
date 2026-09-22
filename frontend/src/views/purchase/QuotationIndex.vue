<template>
  <div>
    <el-card>
      <PageHead title="报价管理">
        <el-button type="primary" :icon="Upload" v-permission="'purchase:quotation:write'" @click="openImport">导入报价</el-button>
      </PageHead>
      <div class="toolbar">
        <el-input v-model="inquiryId" placeholder="询价单 ID" clearable style="width: 240px" @keyup.enter="reload" />
        <el-button type="primary" :icon="Search" @click="reload">查询</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="180" />
        <el-table-column prop="batchNo" label="批次" width="220" />
        <el-table-column prop="supplierId" label="供应商" width="200" />
        <el-table-column prop="skuId" label="SKU" width="200" />
        <el-table-column prop="price" label="单价(基本单位)" width="130" align="right" />
        <el-table-column prop="qtyInBaseUnit" label="基本数量" width="100" align="right" />
        <el-table-column prop="purchaseUnit" label="报价单位" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><StatusTag :value="row.status" enum-key="quotationStatus" /></template>
        </el-table-column>
        <el-table-column label="有效" width="70">
          <template #default="{ row }">
            <el-tag v-if="!row.invalid" type="success" size="small">有效</el-tag>
            <el-tag v-else type="info" size="small">失效</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.invalid && row.status === 'SUBMITTED'" link type="success" @click="onAccept(row)">采纳</el-button>
            <el-button v-if="!row.invalid && row.status === 'SUBMITTED'" link type="danger" @click="onReject(row)">否决</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="importVisible" title="导入报价（按模板填写；任一行错误整批不落库）" width="520px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="询价 ID" required><el-input v-model="importForm.inquiryId" /></el-form-item>
        <el-form-item label="报价文件" required>
          <input ref="fileRef" type="file" accept=".xlsx" />
        </el-form-item>
      </el-form>
      <el-alert v-if="importResult" :type="importResult.fail ? 'error' : 'success'" :closable="false" style="margin-top: 8px">
        <div>批次 {{ importResult.batchNo }}：成功 {{ importResult.success }} / 失败 {{ importResult.fail }}</div>
        <div v-for="(e, i) in importResult.errors || []" :key="i" class="err-line">{{ e }}</div>
        <el-button v-if="importResult.errorSheetBase64" link type="primary" @click="onDownloadErrorSheet">下载错误 Sheet</el-button>
      </el-alert>
      <template #footer>
        <el-button @click="importVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importing" @click="onImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Search } from '@element-plus/icons-vue'
import PageHead from '@/components/PageHead.vue'
import StatusTag from '@/components/StatusTag.vue'
import { listQuotations, importQuotations, acceptQuotation, rejectQuotation, downloadErrorSheet } from '@/api/purchase2'

const inquiryId = ref('')
const rows = ref([])
const loading = ref(false)

async function reload() {
  if (!inquiryId.value) {
    rows.value = []
    return
  }
  loading.value = true
  try {
    const res = await listQuotations(inquiryId.value)
    rows.value = res.data || []
  } finally {
    loading.value = false
  }
}

// ---- 导入（全有或全无 + 错误 Sheet） ----
const importVisible = ref(false)
const importing = ref(false)
const importForm = reactive({ inquiryId: '' })
const importResult = ref(null)
const fileRef = ref(null)

function openImport() {
  importForm.inquiryId = inquiryId.value
  importResult.value = null
  importVisible.value = true
}
async function onImport() {
  const file = fileRef.value && fileRef.value.files && fileRef.value.files[0]
  if (!importForm.inquiryId || !file) {
    ElMessage.warning('请填写询价 ID 并选择 xlsx 文件')
    return
  }
  importing.value = true
  try {
    const res = await importQuotations(importForm.inquiryId, file)
    importResult.value = res.data
    if (res.data && res.data.success > 0) {
      ElMessage.success(`导入成功 ${res.data.success} 行`)
      inquiryId.value = importForm.inquiryId
      reload()
    } else {
      ElMessage.error('整批未落库：请修正后重新导入')
    }
  } finally {
    importing.value = false
  }
}
function onDownloadErrorSheet() {
  downloadErrorSheet(importResult.value.errorSheetBase64, 'quotation-error-sheet.xlsx')
}

async function onAccept(row) {
  await ElMessageBox.confirm('确认采纳该报价？', '采纳')
  await acceptQuotation(row.id)
  ElMessage.success('已采纳')
  reload()
}
async function onReject(row) {
  await ElMessageBox.confirm('确认否决该报价？', '否决')
  await rejectQuotation(row.id)
  ElMessage.success('已否决')
  reload()
}

onMounted(reload)
</script>

<style scoped>
.err-line {
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-color-danger);
}
</style>
