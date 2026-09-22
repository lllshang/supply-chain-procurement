<template>
  <div>
    <el-card>
      <PageHead title="询价管理">
        <el-button type="primary" :icon="Plus" v-permission="'purchase:inquiry:write'" @click="openCreate">新建询价</el-button>
      </PageHead>
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="inquiryNo" label="询价单号" width="170" />
        <el-table-column prop="applyId" label="来源申请" width="200" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" enum-key="inquiryStatus" /></template>
        </el-table-column>
        <el-table-column prop="deadline" label="截标时间" width="180" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openSuppliers(row)">范围</el-button>
            <el-button link type="primary" @click="openComparison(row)">比价</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="warning" v-permission="'purchase:inquiry:write'" @click="onPublish(row)">发布</el-button>
            <el-button v-if="row.status === 'PUBLISHED'" link type="warning" v-permission="'purchase:inquiry:write'" @click="onClose(row)">截标</el-button>
            <el-button v-if="row.status === 'PUBLISHED'" link type="primary" @click="onTemplate(row)">模板</el-button>
            <el-button v-if="['DRAFT', 'PUBLISHED'].includes(row.status)" link type="danger" v-permission="'purchase:inquiry:write'" @click="onCancel(row)">取消</el-button>
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
    </el-card>

    <!-- 新建询价 -->
    <el-dialog v-model="formVisible" title="新建询价" width="560px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="来源申请ID" required><el-input v-model="form.applyId" placeholder="已审批申请的 ID" /></el-form-item>
        <el-form-item label="截标时间" required><el-date-picker v-model="form.deadline" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" /></el-form-item>
        <el-form-item label="供应商ID">
          <el-input v-model="form.supplierIdsText" placeholder="逗号分隔的供应商 ID（可空，发布前再圈定）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 供应商范围 -->
    <el-drawer v-model="scopeVisible" title="供应商范围（发布时逐家准入校验，不合格剔除留痕）" size="55%">
      <div style="margin-bottom: 10px">
        <el-input v-model="scopeAddText" placeholder="增补供应商 ID（逗号分隔）" style="width: 320px" />
        <el-button type="primary" v-permission="'purchase:inquiry:write'" @click="onAddSuppliers">增补</el-button>
      </div>
      <el-table :data="scopeRows" stripe size="small">
        <el-table-column prop="supplierId" label="供应商" width="200" />
        <el-table-column label="在范围内" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.invited" type="success" size="small">是</el-tag>
            <el-tag v-else type="info" size="small">剔除</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="准入快照" min-width="280">
          <template #default="{ row }"><span class="snap">{{ row.admissionSnapshot || '-' }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button v-if="row.invited" link type="danger" v-permission="'purchase:inquiry:write'" @click="onRemoveSupplier(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <!-- 比价 -->
    <el-drawer v-model="cmpVisible" title="比价视图（最低 / 最高 / 均价 + 历史价）" size="55%">
      <el-table :data="cmpRows" stripe size="small">
        <el-table-column prop="skuId" label="SKU" width="200" />
        <el-table-column prop="minPrice" label="最低价" width="100" align="right" />
        <el-table-column prop="maxPrice" label="最高价" width="100" align="right" />
        <el-table-column prop="avgPrice" label="均价" width="100" align="right" />
        <el-table-column label="报价明细" min-width="320">
          <template #default="{ row }">
            <div v-for="q in row.quotations" :key="q.id" class="quot-line">
              供应商 {{ q.supplierId }} · {{ q.price }}/{{ q.purchaseUnit }} · {{ q.qtyInBaseUnit }}基本 · {{ q.batchNo }}
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import PageHead from '@/components/PageHead.vue'
import StatusTag from '@/components/StatusTag.vue'
import { usePagination } from '@/composables/usePagination'
import {
  pageInquiries, createInquiry, publishInquiry, closeInquiry, cancelInquiry,
  listInquirySuppliers, addInquirySuppliers, removeInquirySupplier,
  getInquiryComparison, downloadQuotationTemplate, saveBlob
} from '@/api/purchase2'

const rows = ref([])
const { current, pageSize, total, loading, load, onCurrentChange } = usePagination((p) => pageInquiries(p))

function reload() {
  return load().then((data) => { rows.value = data })
}
function handlePage(p) {
  onCurrentChange(p).then((data) => { rows.value = data })
}

// ---- 新建 ----
const formVisible = ref(false)
const saving = ref(false)
const form = reactive({ applyId: '', deadline: '', supplierIdsText: '' })
function openCreate() {
  Object.assign(form, { applyId: '', deadline: '', supplierIdsText: '' })
  formVisible.value = true
}
async function onSave() {
  if (!form.applyId || !form.deadline) {
    ElMessage.warning('请填写来源申请与截标时间')
    return
  }
  saving.value = true
  try {
    const supplierIds = form.supplierIdsText.split(/[,，\s]+/).filter(Boolean)
    // 雪花 ID 全程字符串直传（#28）：Number() 会丢失 >2^53 精度，后端 Long→String 序列化
    await createInquiry({ applyId: form.applyId, deadline: form.deadline, supplierIds })
    ElMessage.success('询价已创建')
    formVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

async function onPublish(row) {
  await ElMessageBox.confirm('发布将逐家调用准入校验：不合格供应商剔除并留痕，剔除后无可用供应商则拒绝。确认发布？', '发布询价')
  await publishInquiry(row.id)
  ElMessage.success('已发布')
  reload()
}
async function onClose(row) {
  await ElMessageBox.confirm('截标后供应商不能再报价，可进入定标。确认截标？', '截标')
  await closeInquiry(row.id)
  ElMessage.success('已截标')
  reload()
}
async function onCancel(row) {
  await ElMessageBox.confirm('确认取消该询价？', '取消询价')
  await cancelInquiry(row.id)
  ElMessage.success('已取消')
  reload()
}

async function onTemplate(row) {
  const blob = await downloadQuotationTemplate(row.id)
  saveBlob(blob, `quotation-template-${row.inquiryNo || row.id}.xlsx`)
}

// ---- 范围 ----
const scopeVisible = ref(false)
const scopeRows = ref([])
const scopeAddText = ref('')
const currentInquiry = ref(null)
async function openSuppliers(row) {
  currentInquiry.value = row
  scopeAddText.value = ''
  const res = await listInquirySuppliers(row.id)
  scopeRows.value = res.data || []
  scopeVisible.value = true
}
async function onAddSuppliers() {
  const ids = scopeAddText.value.split(/[,，\s]+/).filter(Boolean)
  if (!ids.length) {
    ElMessage.warning('请输入供应商 ID')
    return
  }
  await addInquirySuppliers(currentInquiry.value.id, ids)
  ElMessage.success('已增补（不合格会被拒绝）')
  openSuppliers(currentInquiry.value)
}
async function onRemoveSupplier(row) {
  await removeInquirySupplier(currentInquiry.value.id, row.supplierId)
  ElMessage.success('已移除')
  openSuppliers(currentInquiry.value)
}

// ---- 比价 ----
const cmpVisible = ref(false)
const cmpRows = ref([])
async function openComparison(row) {
  const res = await getInquiryComparison(row.id)
  cmpRows.value = (res.data && res.data.items) || []
  cmpVisible.value = true
}

onMounted(reload)
</script>

<style scoped>
.snap {
  font-size: 12px;
  color: var(--app-text-weak, #999);
  word-break: break-all;
}
.quot-line {
  font-size: 12px;
  line-height: 1.8;
  color: var(--app-text-secondary, #666);
}
</style>
