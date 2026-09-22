<template>
  <div>
    <el-card>
      <PageHead title="采购申请">
        <el-button type="primary" :icon="Plus" v-permission="'purchase:apply:write'" @click="openCreate">新建申请</el-button>
      </PageHead>
      <div class="toolbar">
        <el-input v-model="queryTitle" placeholder="按标题搜索" clearable style="width: 220px" @keyup.enter="reload" />
        <EnumSelect v-model="queryStatus" enum-key="applyStatus" placeholder="状态" style="--filter-width: 140px" />
        <el-button type="primary" :icon="Search" @click="reload">查询</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="applyNo" label="申请单号" width="170" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="140">
          <template #default="{ row }"><StatusTag :value="row.type" enum-key="applyType" /></template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" enum-key="applyStatus" /></template>
        </el-table-column>
        <el-table-column label="预算" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.budgetStatus === 2" type="danger" size="small">超限</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="expectedDate" label="期望到货" width="120" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">明细</el-button>
            <el-button v-if="['DRAFT', 'REJECTED'].includes(row.status)" link type="primary" v-permission="'purchase:apply:write'" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="['DRAFT', 'REJECTED'].includes(row.status)" link type="warning" v-permission="'purchase:apply:write'" @click="onSubmit(row)">提交</el-button>
            <el-button link type="primary" v-permission="'purchase:apply:export'" @click="onExport(row)">导出</el-button>
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

    <!-- 新建 / 编辑 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑申请' : '新建申请'" width="860px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="标题" required><el-input v-model="form.title" placeholder="申请标题" /></el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="类型">
              <EnumSelect v-model="form.type" enum-key="applyType" :clearable="false" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="期望到货"><el-date-picker v-model="form.expectedDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="明细行">
          <el-button size="small" :icon="Plus" @click="addItem">加行</el-button>
          <el-button size="small" :icon="Goods" @click="onBringIn">常购带入</el-button>
        </el-form-item>
        <el-table :data="form.items" stripe size="small">
          <el-table-column label="SKU ID" width="200">
            <template #default="{ row }"><el-input v-model="row.skuId" placeholder="SKU ID" /></template>
          </el-table-column>
          <el-table-column label="数量" width="110">
            <template #default="{ row }"><el-input-number v-model="row.qty" :min="0.01" :controls="false" style="width: 90px" /></template>
          </el-table-column>
          <el-table-column label="采购单位" width="110">
            <template #default="{ row }"><el-input v-model="row.purchaseUnit" placeholder="BOX" /></template>
          </el-table-column>
          <el-table-column label="预估单价" width="130">
            <template #default="{ row }"><el-input-number v-model="row.priceEstimate" :min="0" :controls="false" style="width: 110px" /></template>
          </el-table-column>
          <el-table-column label="行类型" width="110">
            <template #default="{ row }"><EnumSelect v-model="row.itemType" enum-key="itemType" :clearable="false" /></template>
          </el-table-column>
          <el-table-column label="备注" min-width="120">
            <template #default="{ row }"><el-input v-model="row.remark" /></template>
          </el-table-column>
          <el-table-column label="" width="60">
            <template #default="$index_scope"><el-button link type="danger" @click="form.items.splice($index_scope.$index, 1)">删</el-button></template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 明细 -->
    <el-drawer v-model="detailVisible" title="申请明细" size="62%">
      <el-descriptions v-if="detail.apply" :column="3" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="单号">{{ detail.apply.applyNo }}</el-descriptions-item>
        <el-descriptions-item label="标题">{{ detail.apply.title }}</el-descriptions-item>
        <el-descriptions-item label="状态"><StatusTag :value="detail.apply.status" enum-key="applyStatus" /></el-descriptions-item>
      </el-descriptions>
      <el-table :data="detail.items" stripe size="small">
        <el-table-column prop="skuId" label="SKU" width="180" />
        <el-table-column prop="qtyInPurchaseUnit" label="采购数量" width="100" align="right" />
        <el-table-column prop="purchaseUnit" label="单位" width="80" />
        <el-table-column prop="convRateSnapshot" label="换算率" width="90" align="right" />
        <el-table-column prop="qtyInBaseUnit" label="基本数量" width="100" align="right" />
        <el-table-column prop="priceEstimate" label="预估单价" width="100" align="right" />
        <el-table-column label="行类型" width="90">
          <template #default="{ row }"><StatusTag :value="row.itemType" enum-key="itemType" /></template>
        </el-table-column>
        <el-table-column prop="orderedQty" label="已转单" width="90" align="right" />
        <el-table-column prop="remainQty" label="余量" width="90" align="right" />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Goods } from '@element-plus/icons-vue'
import PageHead from '@/components/PageHead.vue'
import StatusTag from '@/components/StatusTag.vue'
import EnumSelect from '@/components/EnumSelect.vue'
import { usePagination } from '@/composables/usePagination'
import {
  pageApplies, getApplyDetail, createApply, updateApply, submitApply, exportApply,
  frequentBringIn, saveBlob
} from '@/api/purchase2'

const queryTitle = ref('')
const queryStatus = ref(null)
const rows = ref([])
const { current, pageSize, total, loading, load, onCurrentChange } = usePagination((p) =>
  pageApplies({ title: queryTitle.value || undefined, status: queryStatus.value || undefined, ...p })
)

function reload() {
  return load().then((data) => { rows.value = data })
}
function handlePage(p) {
  onCurrentChange(p).then((data) => { rows.value = data })
}

// ---- 新建 / 编辑 ----
const formVisible = ref(false)
const saving = ref(false)
const form = reactive({ id: null, title: '', type: 'STANDARD', expectedDate: null, items: [] })

function emptyItem() {
  return { skuId: '', qty: 1, purchaseUnit: '', priceEstimate: 0, itemType: 'MATERIAL', remark: '' }
}
function addItem() {
  form.items.push(emptyItem())
}
function openCreate() {
  Object.assign(form, { id: null, title: '', type: 'STANDARD', expectedDate: null, items: [emptyItem()] })
  formVisible.value = true
}
async function openEdit(row) {
  const res = await getApplyDetail(row.id)
  Object.assign(form, {
    id: row.id,
    title: res.data.apply.title,
    type: res.data.apply.type,
    expectedDate: res.data.apply.expectedDate,
    items: (res.data.items || []).map((i) => ({
      skuId: String(i.skuId), qty: i.qtyInPurchaseUnit, purchaseUnit: i.purchaseUnit,
      priceEstimate: i.priceEstimate, itemType: i.itemType, remark: i.remark
    }))
  })
  formVisible.value = true
}

// 常购带入（后端返回草稿明细行）
async function onBringIn() {
  const res = await frequentBringIn({ skuIds: form.items.map((i) => i.skuId).filter(Boolean) })
  if (res.data && res.data.length) {
    form.items = res.data.map((d) => ({
      skuId: String(d.skuId), qty: d.qty || 1, purchaseUnit: d.purchaseUnit || '',
      priceEstimate: d.priceEstimate || 0, itemType: d.itemType || 'MATERIAL', remark: d.remark || ''
    }))
  } else {
    ElMessage.info('常购清单为空')
  }
}

async function onSave() {
  if (!form.title) {
    ElMessage.warning('请填写申请标题')
    return
  }
  saving.value = true
  try {
    const payload = {
      title: form.title,
      type: form.type,
      expectedDate: form.expectedDate,
      items: form.items.filter((i) => i.skuId).map((i) => ({
        skuId: i.skuId, qty: i.qty, purchaseUnit: i.purchaseUnit,
        priceEstimate: i.priceEstimate, itemType: i.itemType, remark: i.remark
      }))
    }
    if (form.id) {
      await updateApply(form.id, payload)
    } else {
      await createApply(payload)
    }
    ElMessage.success('保存成功')
    formVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

async function onSubmit(row) {
  await ElMessageBox.confirm(`确认提交申请「${row.title}」进入两级审批？`, '提交确认')
  await submitApply(row.id)
  ElMessage.success('已提交审批')
  reload()
}

// ---- 明细 ----
const detailVisible = ref(false)
const detail = reactive({ apply: null, items: [] })
async function openDetail(row) {
  const res = await getApplyDetail(row.id)
  detail.apply = res.data.apply
  detail.items = res.data.items || []
  detailVisible.value = true
}

async function onExport(row) {
  const blob = await exportApply(row.id)
  saveBlob(blob, `purchase-apply-${row.applyNo || row.id}.xlsx`)
}

onMounted(reload)
</script>
