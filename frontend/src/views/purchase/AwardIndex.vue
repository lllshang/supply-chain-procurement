<template>
  <div>
    <el-card>
      <PageHead title="比价定标">
        <el-button type="primary" :icon="Plus" v-permission="'purchase:award:write'" @click="openCreate">新建定标</el-button>
      </PageHead>
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="awardNo" label="定标单号" width="170" />
        <el-table-column prop="inquiryId" label="来源询价" width="200" />
        <el-table-column prop="supplierId" label="主供应商" width="200" />
        <el-table-column prop="amount" label="定标金额" width="120" align="right" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" enum-key="awardStatus" /></template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openItems(row)">明细</el-button>
            <el-button v-if="row.status === 'REJECTED'" link type="primary" v-permission="'purchase:award:write'" @click="openCreate(row)">调整重提</el-button>
            <el-button v-if="['PENDING_APPROVAL', 'REJECTED'].includes(row.status)" link type="warning" v-permission="'purchase:award:submit'" @click="onSubmit(row)">提交审批</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        background
        layout="total, prev, pager, next"
        :total="total"
        v-model:current-page="current"
        :page-size="pageSize"
        @current-change="handlePage"
      />
    </el-card>

    <!-- 新建 / 调整 -->
    <el-dialog v-model="formVisible" :title="form.editId ? '调整定标明细（重提）' : '新建定标'" width="860px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="来源询价ID" required><el-input v-model="form.inquiryId" :disabled="!!form.editId" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="来源申请ID"><el-input v-model="form.applyId" :disabled="!!form.editId" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="定标供应商" required>
              <el-input v-model="form.supplierId" placeholder="单一中标供应商ID" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="定标明细">
          <el-button size="small" :icon="Plus" @click="addItem">加行</el-button>
          <span class="hint">R2：一询价单一中标供应商——明细行统一使用上方供应商，不再按 SKU 拆多供应商。</span>
        </el-form-item>
        <el-table :data="form.items" stripe size="small">
          <el-table-column label="SKU ID" width="240">
            <template #default="{ row }"><el-input v-model="row.skuId" /></template>
          </el-table-column>
          <el-table-column label="单价(基本单位)" width="150">
            <template #default="{ row }"><el-input-number v-model="row.price" :min="0.01" :controls="false" style="width: 130px" /></template>
          </el-table-column>
          <el-table-column label="数量(采购单位)" width="160">
            <template #default="{ row }"><el-input-number v-model="row.qty" :min="0.01" :controls="false" style="width: 140px" /></template>
          </el-table-column>
          <el-table-column label="" width="60">
            <template #default="$index_scope"><el-button link type="danger" @click="form.items.splice($index_scope.$index, 1)">删</el-button></template>
          </el-table-column>
        </el-table>
        <el-form-item label="备注" style="margin-top: 12px"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">{{ form.editId ? '保存调整' : '创建' }}</el-button>
      </template>
    </el-dialog>

    <!-- 明细 -->
    <el-drawer v-model="itemsVisible" title="定标明细（换算快照）" size="55%">
      <el-table :data="itemRows" stripe size="small">
        <el-table-column prop="skuId" label="SKU" width="200" />
        <el-table-column prop="supplierId" label="供应商" width="200" />
        <el-table-column prop="qtyInPurchaseUnit" label="采购数量" width="100" align="right" />
        <el-table-column prop="qtyInBaseUnit" label="基本数量" width="100" align="right" />
        <el-table-column prop="convRateSnapshot" label="换算率" width="90" align="right" />
        <el-table-column prop="price" label="单价" width="100" align="right" />
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
  pageAwards, createAward, updateAwardItems, submitAward, listAwardItems, getInquiryComparison
} from '@/api/purchase2'

const rows = ref([])
const { current, pageSize, total, loading, load, onCurrentChange } = usePagination((p) => pageAwards(p))

function reload() {
  return load().then((data) => { rows.value = data })
}
function handlePage(p) {
  onCurrentChange(p).then((data) => { rows.value = data })
}

// ---- 新建 / 调整 ----
const formVisible = ref(false)
const saving = ref(false)
const form = reactive({ editId: null, inquiryId: '', applyId: '', supplierId: '', remark: '', items: [] })

function emptyItem() {
  return { skuId: '', price: 0, qty: 1 }
}
function addItem() {
  form.items.push(emptyItem())
}
function openCreate(row) {
  // row 传参 = REJECTED 调整重提（带出原询价/申请/供应商）
  Object.assign(form, {
    editId: row && row.status === 'REJECTED' ? row.id : null,
    inquiryId: row ? String(row.inquiryId || '') : '',
    applyId: row ? String(row.applyId || '') : '',
    supplierId: row && row.supplierId ? String(row.supplierId) : '',
    remark: '',
    items: [emptyItem()]
  })
  formVisible.value = true
}

async function onSave() {
  // R2：单一中标供应商——表头统一选择，逐行注入
  const items = form.items.filter((i) => i.skuId).map((i) => ({
    skuId: i.skuId, supplierId: form.supplierId, price: i.price, qty: i.qty
  }))
  if (!form.inquiryId || !form.supplierId || !items.length) {
    ElMessage.warning('请填写询价 ID、定标供应商与完整明细')
    return
  }
  saving.value = true
  try {
    if (form.editId) {
      await updateAwardItems(form.editId, { inquiryId: form.inquiryId, applyId: form.applyId || null, items, remark: form.remark })
      ElMessage.success('已调整，请提交审批')
    } else {
      await createAward({ inquiryId: form.inquiryId, applyId: form.applyId || null, items, remark: form.remark })
      ElMessage.success('定标已创建，请提交审批')
    }
    formVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

async function onSubmit(row) {
  await ElMessageBox.confirm('提交后走 AWARD 审批（异常价会提示人工复核）。确认提交？', '提交审批')
  await submitAward(row.id)
  ElMessage.success('已提交审批')
  reload()
}

// ---- 明细 ----
const itemsVisible = ref(false)
const itemRows = ref([])
async function openItems(row) {
  const res = await listAwardItems(row.id)
  itemRows.value = res.data || []
  itemsVisible.value = true
}

// 比价视图可从询价页进入；此处保留引用避免未使用告警
void getInquiryComparison

onMounted(reload)
</script>

<style scoped>
.hint {
  margin-left: 10px;
  font-size: 12px;
  color: var(--app-text-weak, #999);
}
</style>
