<template>
  <div>
    <el-card>
      <PageHead title="结算管理">
        <el-button :icon="Coin" @click="openPrepay">发起预付款</el-button>
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
        <el-table-column label="类型" width="110">
          <template #default="{ row }"><StatusTag :value="row.type" enum-key="settlementType" /></template>
        </el-table-column>
        <el-table-column label="付款阶段" width="90">
          <template #default="{ row }">{{ row.paymentStage == null ? '-' : ({ 1: '预付款', 2: '进度款', 3: '尾款' })[row.paymentStage] }}</template>
        </el-table-column>
        <el-table-column label="结算方式" width="90">
          <template #default="{ row }">{{ enumLabel('settleMode', row.settleMode) }}</template>
        </el-table-column>
        <el-table-column prop="settledQtyBase" label="结算数量" width="100" align="right" />
        <el-table-column prop="deductAmount" label="考核扣款" width="100" align="right" />
        <el-table-column prop="amount" label="结算金额" width="120" align="right" />
        <el-table-column label="预付款抵扣" width="110" align="right">
          <template #default="{ row }">{{ row.prepaymentDeduction != null && row.prepaymentDeduction > 0 ? ('¥' + row.prepaymentDeduction) : '-' }}</template>
        </el-table-column>
        <el-table-column label="付款状态" width="130">
          <template #default="{ row }">
            <StatusTag v-if="row.payStatus" :value="row.payStatus" enum-key="settlementPayStatus" />
            <span v-else>-</span>
            <span v-if="row.payStatus && row.paidProgress != null && row.paidProgress > 0" style="margin-left: 4px">{{ Math.round(row.paidProgress * 100) }}%</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" enum-key="settlementStatus" /></template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">明细</el-button>
            <el-button v-if="row.status === 'PENDING'" link type="warning" @click="openEdit(row)">编辑重提</el-button>
            <el-button v-if="row.status === 'PENDING'" link type="success" @click="onSubmit(row)">提交审批</el-button>
            <el-button v-if="row.status === 'PENDING'" link type="danger" @click="onVoid(row)">作废</el-button>
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

    <!-- 发起预付款（D10：订单→SETTLEMENT 审批→实付→尾款自动扣减） -->
    <el-dialog v-model="prepayVisible" title="发起预付款" width="640px" destroy-on-close>
      <el-form :model="prepayForm" label-width="110px">
        <el-form-item label="订单ID" required>
          <el-input v-model="prepayForm.orderId" placeholder="订单ID" :disabled="prepayDraftLoaded" />
        </el-form-item>
        <el-form-item label-width="10px">
          <el-button :disabled="prepayDraftLoaded" :loading="prepayDrafting" @click="loadPrepayDraft">带出预付款草稿</el-button>
        </el-form-item>
        <el-descriptions v-if="prepayDraft" :column="3" border size="small" style="margin-bottom: 12px">
          <el-descriptions-item label="订单号">{{ prepayDraft.orderNo || prepayForm.orderId }}</el-descriptions-item>
          <el-descriptions-item label="订单金额">{{ prepayDraft.orderAmount }}</el-descriptions-item>
          <el-descriptions-item label="已付预付款">{{ prepayDraft.prepaidPaid }}</el-descriptions-item>
          <el-descriptions-item label="可发起余额" :span="3">
            <b style="color: var(--el-color-danger)">{{ prepayAvailable }}</b>
          </el-descriptions-item>
        </el-descriptions>
        <template v-if="prepayDraft">
          <el-form-item label="预付金额" required>
            <el-input-number v-model="prepayForm.amount" :min="0.01" :max="Number(prepayAvailable) || 0" :controls="false" style="width: 100%" />
          </el-form-item>
          <el-form-item label="备注"><el-input v-model="prepayForm.remark" /></el-form-item>
          <el-alert type="info" :closable="false" show-icon
            title="累计预付款（含在途审批中）不得超过订单有效金额；预付款经 SETTLEMENT 审批、实付登记后，将在尾款单中自动抵扣" />
        </template>
      </el-form>
      <template #footer>
        <el-button @click="prepayVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!prepayDraft" :loading="prepaySaving" @click="onPrepaySave">创建预付款结算单</el-button>
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
        <el-descriptions-item label="付款阶段">{{ detail.paymentStage == null ? '-' : ({ 1: '预付款', 2: '进度款', 3: '尾款' })[detail.paymentStage] }}</el-descriptions-item>
        <el-descriptions-item label="结算方式">{{ enumLabel('settleMode', detail.settleMode) }}</el-descriptions-item>
        <el-descriptions-item label="结算数量">{{ detail.settledQtyBase }}</el-descriptions-item>
        <el-descriptions-item label="考核扣款">{{ detail.deductAmount }}</el-descriptions-item>
        <el-descriptions-item label="结算金额">{{ detail.amount }}</el-descriptions-item>
        <el-descriptions-item label="预付款抵扣">{{ detail.prepaymentDeduction != null && detail.prepaymentDeduction > 0 ? ('¥' + detail.prepaymentDeduction) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="付款状态">
          <template v-if="detail.payStatus">
            <StatusTag :value="detail.payStatus" enum-key="settlementPayStatus" />
            <span v-if="detail.paidProgress != null" style="margin-left: 4px">{{ Math.round(detail.paidProgress * 100) }}%</span>
          </template>
          <template v-else>-</template>
        </el-descriptions-item>
        <el-descriptions-item label="尾款结清">{{ detail.isFinal === 1 ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="合同ID">{{ detail.contractId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Coin } from '@element-plus/icons-vue'
import PageHead from '@/components/PageHead.vue'
import StatusTag from '@/components/StatusTag.vue'
import EnumSelect from '@/components/EnumSelect.vue'
import { usePagination } from '@/composables/usePagination'
import { enumLabel } from '@/constants/enums'
import {
  pageSettlements, getSettlement, draftFromOrder, draftFromArrival,
  createSettlement, updateSettlement, submitSettlement,
  draftPrepaymentFromOrder, createPrepaymentSettlement,
  voidSettlement
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

// ---- 发起预付款（D10：订单发起→SETTLEMENT 审批→实付→尾款自动抵扣） ----
const prepayVisible = ref(false)
const prepayDrafting = ref(false)
const prepayDraftLoaded = ref(false)
const prepaySaving = ref(false)
const prepayDraft = ref(null)
const prepayForm = reactive({ orderId: '', amount: null, remark: '' })
const prepayAvailable = computed(() => {
  if (!prepayDraft.value) return null
  const gross = Number(prepayDraft.value.orderAmount || 0)
  const paid = Number(prepayDraft.value.prepaidPaid || 0)
  return Math.max(0, gross - paid).toFixed(2)
})

function openPrepay() {
  Object.assign(prepayForm, { orderId: '', amount: null, remark: '' })
  prepayDraft.value = null
  prepayDraftLoaded.value = false
  prepayVisible.value = true
}

async function loadPrepayDraft() {
  if (!prepayForm.orderId) {
    ElMessage.warning('请输入订单ID')
    return
  }
  prepayDrafting.value = true
  try {
    const res = await draftPrepaymentFromOrder(prepayForm.orderId)
    prepayDraft.value = res.data
    prepayDraftLoaded.value = true
  } finally {
    prepayDrafting.value = false
  }
}

async function onPrepaySave() {
  if (!prepayForm.amount || prepayForm.amount <= 0) {
    ElMessage.warning('请填写预付金额')
    return
  }
  prepaySaving.value = true
  try {
    await createPrepaymentSettlement(prepayForm.orderId, {
      amount: prepayForm.amount, remark: prepayForm.remark || undefined
    })
    ElMessage.success('预付款结算单已创建，经 SETTLEMENT 审批、实付登记后将在尾款单中自动抵扣')
    prepayVisible.value = false
    reload()
  } finally {
    prepaySaving.value = false
  }
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

// ---- B9：作废 ----
async function onVoid(row) {
  const { value: reason } = await ElMessageBox.prompt('作废后该结算单将从承诺口径排除（预付款释放），不可恢复', '作废结算单', {
    confirmButtonText: '确认作废',
    cancelButtonText: '取消',
    inputPattern: /\S+/,
    inputErrorMessage: '作废原因必填',
    inputPlaceholder: '请输入作废原因',
    type: 'warning'
  })
  await voidSettlement(row.id, { reason })
  ElMessage.success('结算单已作废')
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
