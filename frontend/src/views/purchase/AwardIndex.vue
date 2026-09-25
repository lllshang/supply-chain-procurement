<template>
  <div>
    <el-card>
      <PageHead title="比价定标">
        <el-button type="primary" :icon="Plus" v-permission="'purchase:award:write'" @click="openCreate">新建定标</el-button>
        <el-button :icon="Document" v-permission="'purchase:award:write'" @click="openOfflineCreate">线下定标登记</el-button>
      </PageHead>
      <el-table :data="rows" v-loading="loading" stripe :row-class-name="rowClassName">
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
      <el-form :model="form" label-width="110px">
        <el-form-item label="定标方式" required>
          <el-radio-group v-model="form.entryMode" :disabled="!!form.editId">
            <el-radio value="INQUIRY">询价定标</el-radio>
            <el-radio value="OFFLINE">线下定标登记</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-row :gutter="12">
          <el-col v-if="form.entryMode === 'INQUIRY'" :span="8">
            <el-form-item label="来源询价ID" required><el-input v-model="form.inquiryId" :disabled="!!form.editId" /></el-form-item>
          </el-col>
          <el-col v-if="form.entryMode === 'INQUIRY'" :span="8">
            <el-form-item label="来源申请ID"><el-input v-model="form.applyId" :disabled="!!form.editId" /></el-form-item>
          </el-col>
        </el-row>
        <el-row v-if="form.entryMode === 'OFFLINE'" :gutter="12">
          <el-col :span="8">
            <el-form-item label="预算部门ID" required><el-input v-model="form.deptId" placeholder="预算部门 ID" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="预算科目" required>
              <el-select v-model="form.subjectId" placeholder="提交时即占预算" style="width: 100%">
                <el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <div v-if="form.entryMode === 'OFFLINE'" class="hint" style="margin-bottom: 10px">
          线下定标登记（P2b/D9）：线下已完成比选定标时不补建询价单直接登记；提交审批时即按部门×科目×当月占用预算。
        </div>
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
import { Plus, Document } from '@element-plus/icons-vue'
import PageHead from '@/components/PageHead.vue'
import StatusTag from '@/components/StatusTag.vue'
import { usePagination } from '@/composables/usePagination'
import {
  pageAwards, createAward, updateAwardItems, submitAward, listAwardItems, getAward, getInquiryComparison
} from '@/api/purchase2'
import { listBudgetSubjects } from '@/api/budget'

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
// P2b/D9：entryMode=INQUIRY 询价定标 | OFFLINE 线下定标登记（预算锚点=award，提交即占）
const form = reactive({
  editId: null, entryMode: 'INQUIRY', inquiryId: '', applyId: '',
  deptId: '', subjectId: null, supplierId: '', remark: '', items: []
})

// 预算科目（线下定标登记用）
const subjects = ref([])
async function loadSubjects() {
  try {
    const res = await listBudgetSubjects()
    subjects.value = res.data || []
  } catch (e) {
    subjects.value = []
  }
}

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
    entryMode: 'INQUIRY',
    inquiryId: row ? String(row.inquiryId || '') : '',
    applyId: row ? String(row.applyId || '') : '',
    deptId: '', subjectId: null,
    supplierId: row && row.supplierId ? String(row.supplierId) : '',
    remark: '',
    items: [emptyItem()]
  })
  formVisible.value = true
}

async function openOfflineCreate() {
  Object.assign(form, {
    editId: null, entryMode: 'OFFLINE', inquiryId: '', applyId: '',
    deptId: '1', subjectId: null, supplierId: '', remark: '', items: [emptyItem()]
  })
  await loadSubjects()
  formVisible.value = true
}

async function onSave() {
  // R2：单一中标供应商——表头统一选择，逐行注入
  const items = form.items.filter((i) => i.skuId).map((i) => ({
    skuId: i.skuId, supplierId: form.supplierId, price: i.price, qty: i.qty
  }))
  if (form.entryMode === 'OFFLINE') {
    // D9 线下定标登记：询价ID 留空，部门×科目必填（提交时即占预算）
    if (!form.deptId || !form.subjectId || !form.supplierId || !items.length) {
      ElMessage.warning('线下定标需填写预算部门、预算科目、定标供应商与完整明细')
      return
    }
    saving.value = true
    try {
      await createAward({
        inquiryId: null, applyId: null,
        deptId: form.deptId, subjectId: form.subjectId,
        items, remark: form.remark
      })
      ElMessage.success('线下定标已登记，提交审批时将占用预算')
      formVisible.value = false
      reload()
    } finally {
      saving.value = false
    }
    return
  }
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
  await ElMessageBox.confirm('提交后走 AWARD 审批（异常价会提示人工复核）；系统将按部门×科目×月份再次校验预算（BR-04），预算不足将转预算升级审批。确认提交？', '提交审批')
  await submitAward(row.id)
  // R7 预算再校验已在后端执行；若不足，后端已转 BUDGET 升级审批并写入 remark
  try {
    const res = await getAward(row.id)
    if (res.data && res.data.remark && res.data.remark.includes('预算升级')) {
      ElMessage.warning('预算不足，已转预算升级审批，请关注预算审批任务')
    } else {
      ElMessage.success('已提交审批')
    }
  } catch (e) {
    ElMessage.success('已提交审批')
  }
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
