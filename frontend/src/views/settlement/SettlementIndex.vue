<template>
  <div>
    <el-card>
      <PageHead title="结算管理">
        <el-button type="primary" :icon="Plus" @click="openCreate">新建结算</el-button>
      </PageHead>
      <div class="toolbar">
        <el-input v-model="queryOrderId" placeholder="按订单ID过滤" clearable style="width: 180px" @keyup.enter="reload" />
        <EnumSelect v-model="queryStatus" enum-key="settlementStatus" placeholder="状态" style="--filter-width: 140px" />
        <el-button type="primary" :icon="Search" @click="reload">查询</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="settleNo" label="结算单号" width="170" />
        <el-table-column prop="orderId" label="订单ID" width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }"><StatusTag :value="row.type" enum-key="settlementType" /></template>
        </el-table-column>
        <el-table-column label="结算方式" width="90">
          <template #default="{ row }">{{ enumLabel('settleMode', row.settleMode) }}</template>
        </el-table-column>
        <el-table-column prop="settledQtyBase" label="结算数量" width="100" align="right" />
        <el-table-column prop="deductAmount" label="考核扣款" width="100" align="right" />
        <el-table-column prop="amount" label="结算金额" width="120" align="right" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" enum-key="settlementStatus" /></template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">明细</el-button>
            <el-button v-if="row.status === 'PENDING'" link type="warning" @click="openEdit(row)">编辑重提</el-button>
            <el-button v-if="row.status === 'PENDING'" link type="success" @click="onSubmit(row)">提交审批</el-button>
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

    <!-- 新建 / 编辑重提 -->
    <el-dialog v-model="formVisible" :title="form.editId ? '编辑结算单（重提）' : '新建结算'" width="760px" destroy-on-close>
      <el-form :model="form" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="带出入口">
              <el-radio-group v-model="form.entryType" :disabled="!!form.editId" @change="resetDraft">
                <el-radio value="ORDER">按订单</el-radio>
                <el-radio value="ARRIVAL">按到货单</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="9">
            <el-form-item :label="form.entryType === 'ORDER' ? '订单ID' : '到货单ID'" required>
              <el-input v-model="form.entryId" :disabled="!!form.editId || draftLoaded" placeholder="单据ID" />
            </el-form-item>
          </el-col>
          <el-col :span="7">
            <el-form-item label-width="10px">
              <el-button :disabled="!!form.editId || draftLoaded" :loading="drafting" @click="loadDraft">带出草稿</el-button>
            </el-form-item>
          </el-col>
        </el-row>

        <el-descriptions v-if="draft" :column="3" border size="small" style="margin-bottom: 12px">
          <el-descriptions-item label="订单号">{{ draft.orderNo || draft.orderId }}</el-descriptions-item>
          <el-descriptions-item label="订单类型">{{ draft.orderType === 'SERVICE' ? '服务' : '物料' }}</el-descriptions-item>
          <el-descriptions-item label="应结总额">{{ draft.orderAmount }}</el-descriptions-item>
          <el-descriptions-item label="已入库累计">{{ draft.storedQtyBase }}</el-descriptions-item>
          <el-descriptions-item label="已结算累计">{{ draft.settledQtyBase }}</el-descriptions-item>
          <el-descriptions-item label="可结余量">{{ draft.remainQtyBase }}</el-descriptions-item>
          <el-descriptions-item label="考核扣款">{{ draft.assessDeduct }}</el-descriptions-item>
          <el-descriptions-item label="建议结算金额">{{ draft.suggestAmount }}</el-descriptions-item>
        </el-descriptions>

        <template v-if="draft">
          <el-row :gutter="12">
            <el-col :span="8">
              <el-form-item label="结算数量" required>
                <el-input-number v-model="form.settledQtyBase" :min="0.01" :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="结算金额" required>
                <el-input-number v-model="form.amount" :min="0" :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="结算类型">
                <EnumSelect v-model="form.type" enum-key="settlementType" :clearable="false" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="12">
            <el-col :span="8">
              <el-form-item label="结算方式">
                <EnumSelect v-model="form.settleMode" enum-key="settleMode" :clearable="false" @change="form.phaseNo = null; form.phaseRatio = null" />
              </el-form-item>
            </el-col>
            <el-col v-if="form.settleMode === 'PHASE'" :span="8">
              <el-form-item label="阶段号" required>
                <el-input-number v-model="form.phaseNo" :min="1" :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col v-if="form.settleMode === 'PHASE'" :span="8">
              <el-form-item label="阶段比例%" required>
                <el-input-number v-model="form.phaseRatio" :min="0" :max="100" :controls="false" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="尾款结清">
                <el-switch v-model="form.isFinal" :active-value="1" :inactive-value="0" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!draft" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 明细 -->
    <el-drawer v-model="detailVisible" title="结算单明细" size="46%">
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="结算单号">{{ detail.settleNo }}</el-descriptions-item>
        <el-descriptions-item label="状态"><StatusTag :value="detail.status" enum-key="settlementStatus" /></el-descriptions-item>
        <el-descriptions-item label="订单ID">{{ detail.orderId }}</el-descriptions-item>
        <el-descriptions-item label="到货单ID">{{ detail.arrivalId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="结算类型">{{ enumLabel('settlementType', detail.type) }}</el-descriptions-item>
        <el-descriptions-item label="结算方式">{{ enumLabel('settleMode', detail.settleMode) }}</el-descriptions-item>
        <el-descriptions-item label="结算数量">{{ detail.settledQtyBase }}</el-descriptions-item>
        <el-descriptions-item label="考核扣款">{{ detail.deductAmount }}</el-descriptions-item>
        <el-descriptions-item label="结算金额">{{ detail.amount }}</el-descriptions-item>
        <el-descriptions-item label="尾款结清">{{ detail.isFinal === 1 ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="合同ID">{{ detail.contractId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import PageHead from '@/components/PageHead.vue'
import StatusTag from '@/components/StatusTag.vue'
import EnumSelect from '@/components/EnumSelect.vue'
import { usePagination } from '@/composables/usePagination'
import { enumLabel } from '@/constants/enums'
import {
  pageSettlements, getSettlement, draftFromOrder, draftFromArrival,
  createSettlement, updateSettlement, submitSettlement
} from '@/api/settlement'

const queryOrderId = ref('')
const queryStatus = ref(null)
const rows = ref([])
const { current, pageSize, total, loading, load, onCurrentChange } = usePagination((p) =>
  pageSettlements({ orderId: queryOrderId.value || undefined, status: queryStatus.value || undefined, ...p })
)

function reload() {
  return load().then((data) => { rows.value = data })
}
function handlePage(p) {
  onCurrentChange(p).then((data) => { rows.value = data })
}

// ---- 新建 / 编辑（双入口：订单 / 到货，先带草稿再保存） ----
const formVisible = ref(false)
const saving = ref(false)
const drafting = ref(false)
const draft = ref(null)
const draftLoaded = ref(false)
const form = reactive({
  editId: null, entryType: 'ORDER', entryId: '',
  settledQtyBase: null, amount: null, type: 'MATERIAL', settleMode: 'ONE_TIME',
  phaseNo: null, phaseRatio: null, isFinal: 0, remark: ''
})

function openCreate() {
  Object.assign(form, {
    editId: null, entryType: 'ORDER', entryId: '',
    settledQtyBase: null, amount: null, type: 'MATERIAL', settleMode: 'ONE_TIME',
    phaseNo: null, phaseRatio: null, isFinal: 0, remark: ''
  })
  draft.value = null
  draftLoaded.value = false
  formVisible.value = true
}

function resetDraft() {
  draft.value = null
  draftLoaded.value = false
}

async function loadDraft() {
  if (!form.entryId) {
    ElMessage.warning(form.entryType === 'ORDER' ? '请输入订单ID' : '请输入到货单ID')
    return
  }
  drafting.value = true
  try {
    const res = form.entryType === 'ORDER'
      ? await draftFromOrder(form.entryId)
      : await draftFromArrival(form.entryId)
    draft.value = res.data
    draftLoaded.value = true
    // 草稿预填：可结余量 + 建议金额（服务单已自动扣考核扣款）
    form.settledQtyBase = draft.value.remainQtyBase ?? null
    form.amount = draft.value.suggestAmount ?? null
    form.type = draft.value.orderType === 'SERVICE' ? 'SERVICE' : 'MATERIAL'
  } finally {
    drafting.value = false
  }
}

async function openEdit(row) {
  const res = await getSettlement(row.id)
  const d = res.data
  Object.assign(form, {
    editId: d.id,
    entryType: d.arrivalId ? 'ARRIVAL' : 'ORDER',
    entryId: String(d.arrivalId || d.orderId),
    settledQtyBase: d.settledQtyBase, amount: d.amount, type: d.type,
    settleMode: d.settleMode, phaseNo: d.phaseNo, phaseRatio: d.phaseRatio,
    isFinal: d.isFinal ?? 0, remark: d.remark || ''
  })
  // 编辑态展示原草稿要素（不再调 draft 接口，避免重复结算拦截）
  draft.value = {
    orderId: d.orderId, orderNo: null, orderType: d.type === 'SERVICE' ? 'SERVICE' : 'MATERIAL',
    remainQtyBase: null, suggestAmount: null, assessDeduct: d.deductAmount
  }
  draftLoaded.value = true
  formVisible.value = true
}

async function onSave() {
  if (!form.settledQtyBase || form.amount == null) {
    ElMessage.warning('请填写结算数量与结算金额')
    return
  }
  if (form.settleMode === 'PHASE' && (!form.phaseNo || !form.phaseRatio)) {
    ElMessage.warning('阶段结算需填写阶段号与阶段比例')
    return
  }
  saving.value = true
  try {
    const payload = {
      orderId: form.entryType === 'ORDER' ? form.entryId : null,
      arrivalId: form.entryType === 'ARRIVAL' ? form.entryId : null,
      settledQtyBase: form.settledQtyBase, amount: form.amount, type: form.type,
      settleMode: form.settleMode, phaseNo: form.phaseNo, phaseRatio: form.phaseRatio,
      isFinal: form.isFinal, remark: form.remark
    }
    if (form.editId) {
      await updateSettlement(form.editId, payload)
      ElMessage.success('已修改，请提交审批')
    } else {
      await createSettlement(payload)
      ElMessage.success('结算单已创建')
    }
    formVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

async function onSubmit(row) {
  await ElMessageBox.confirm('提交后走 SETTLEMENT 审批，审批通过将核销预算占用（writeOff）。确认提交？', '提交审批')
  await submitSettlement(row.id)
  ElMessage.success('已提交结算审批')
  reload()
}

// ---- 明细 ----
const detailVisible = ref(false)
const detail = ref(null)
async function openDetail(row) {
  const res = await getSettlement(row.id)
  detail.value = res.data
  detailVisible.value = true
}

onMounted(reload)
</script>

<style scoped>
.toolbar { margin-bottom: 12px; display: flex; gap: 8px; }
.pager { margin-top: 16px; justify-content: flex-end; display: flex; }
</style>
