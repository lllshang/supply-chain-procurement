<template>
  <div>
    <el-card>
      <PageHead title="付款登记">
        <el-button type="primary" :icon="Plus" @click="openCreate">新建付款单</el-button>
        <el-button :icon="Document" @click="stmtVisible = true">供应商对账单</el-button>
      </PageHead>
      <div class="toolbar">
        <el-input v-model="querySettlementId" placeholder="按结算单ID过滤" clearable style="width: 180px" @keyup.enter="reload" />
        <EnumSelect v-model="queryStatus" enum-key="paymentStatus" placeholder="状态" style="--filter-width: 140px" />
        <el-button type="primary" :icon="Search" @click="reload">查询</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="payNo" label="付款单号" width="170" />
        <el-table-column prop="settlementId" label="结算单ID" width="180" show-overflow-tooltip />
        <el-table-column prop="payAmount" label="付款金额" width="120" align="right" />
        <el-table-column prop="payMethod" label="付款方式" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" enum-key="paymentStatus" /></template>
        </el-table-column>
        <el-table-column prop="payDate" label="付款日期" width="120" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'UNPAID'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'UNPAID'" link type="success" @click="openConfirm(row)">登记确认</el-button>
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

    <!-- 新建 / 编辑 -->
    <el-dialog v-model="formVisible" :title="form.editId ? '编辑付款单（重提）' : '新建付款单'" width="520px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="结算单ID" required>
          <el-input v-model="form.settlementId" :disabled="!!form.editId" placeholder="已审批结算单ID" />
        </el-form-item>
        <el-form-item label="付款金额" required>
          <el-input-number v-model="form.payAmount" :min="0.01" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="付款方式">
          <el-input v-model="form.payMethod" placeholder="银行转账/承兑等" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 登记确认（R6：免审批，财务线下付款登记） -->
    <el-dialog v-model="confirmVisible" title="线下付款登记确认" width="480px" destroy-on-close>
      <el-form :model="confirmForm" label-width="100px">
        <el-form-item label="付款单号">
          <span>{{ confirmForm.payNo }}</span>
        </el-form-item>
        <el-form-item label="付款日期" required>
          <el-date-picker v-model="confirmForm.payDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="付款凭证">
          <el-input v-model="confirmForm.voucherFile" placeholder="凭证文件Key/编号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button type="primary" :loading="confirming" @click="onConfirm">确认付款</el-button>
      </template>
    </el-dialog>

    <!-- 供应商对账单 -->
    <el-dialog v-model="stmtVisible" title="供应商对账单" width="720px" destroy-on-close>
      <el-form inline label-width="90px">
        <el-form-item label="供应商ID"><el-input v-model="stmt.supplierId" style="width: 160px" /></el-form-item>
        <el-form-item label="从">
          <el-date-picker v-model="stmt.from" type="date" value-format="YYYY-MM-DD" style="width: 140px" />
        </el-form-item>
        <el-form-item label="至">
          <el-date-picker v-model="stmt.to" type="date" value-format="YYYY-MM-DD" style="width: 140px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="stmtLoading" @click="loadStatement">查询</el-button>
          <el-button :disabled="!statement" @click="onExportStatement">导出Excel</el-button>
        </el-form-item>
      </el-form>
      <el-descriptions v-if="statement" :column="3" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="应付合计">{{ statement.payableAmount ?? statement.payable }}</el-descriptions-item>
        <el-descriptions-item label="已付合计">{{ statement.paidAmount ?? statement.paid }}</el-descriptions-item>
        <el-descriptions-item label="差额">{{ statement.balanceAmount ?? statement.balance }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="statement ? (statement.rows || []) : []" stripe size="small" max-height="360">
        <el-table-column prop="date" label="日期" width="120" />
        <el-table-column prop="docNo" label="单号" min-width="160" />
        <el-table-column prop="direction" label="方向" width="90" />
        <el-table-column prop="amount" label="金额" width="120" align="right" />
        <el-table-column prop="remark" label="备注" min-width="120" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Document } from '@element-plus/icons-vue'
import PageHead from '@/components/PageHead.vue'
import StatusTag from '@/components/StatusTag.vue'
import EnumSelect from '@/components/EnumSelect.vue'
import { usePagination } from '@/composables/usePagination'
import { saveBlob } from '@/api/purchase2'
import {
  pagePayments, getPayment, createPayment, updatePayment, confirmPayment,
  getStatement, exportStatement
} from '@/api/settlement'

const querySettlementId = ref('')
const queryStatus = ref(null)
const rows = ref([])
const { current, pageSize, total, loading, load, onCurrentChange } = usePagination((p) =>
  pagePayments({ settlementId: querySettlementId.value || undefined, status: queryStatus.value || undefined, ...p })
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
const form = reactive({ editId: null, settlementId: '', payAmount: null, payMethod: '', remark: '' })

function openCreate() {
  Object.assign(form, { editId: null, settlementId: '', payAmount: null, payMethod: '', remark: '' })
  formVisible.value = true
}

async function openEdit(row) {
  const res = await getPayment(row.id)
  const d = res.data
  Object.assign(form, {
    editId: d.id, settlementId: String(d.settlementId), payAmount: d.payAmount,
    payMethod: d.payMethod || '', remark: d.remark || ''
  })
  formVisible.value = true
}

async function onSave() {
  if (!form.settlementId || !form.payAmount) {
    ElMessage.warning('请填写结算单ID与付款金额')
    return
  }
  saving.value = true
  try {
    const payload = {
      settlementId: form.settlementId, payAmount: form.payAmount,
      payMethod: form.payMethod, remark: form.remark
    }
    if (form.editId) {
      await updatePayment(form.editId, payload)
      ElMessage.success('已修改')
    } else {
      await createPayment(payload)
      ElMessage.success('付款单已创建')
    }
    formVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

// ---- 登记确认（R6：免审批；确认后 PAID，无预算动作——核销已在结算完成） ----
const confirmVisible = ref(false)
const confirming = ref(false)
const confirmForm = reactive({ id: null, payNo: '', payDate: '', voucherFile: '' })

function openConfirm(row) {
  Object.assign(confirmForm, { id: row.id, payNo: row.payNo, payDate: '', voucherFile: '' })
  confirmVisible.value = true
}

async function onConfirm() {
  if (!confirmForm.payDate) {
    ElMessage.warning('请选择付款日期')
    return
  }
  confirming.value = true
  try {
    await confirmPayment(confirmForm.id, {
      voucherFile: confirmForm.voucherFile || null,
      payDate: confirmForm.payDate
    })
    ElMessage.success('已登记确认付款')
    confirmVisible.value = false
    reload()
  } finally {
    confirming.value = false
  }
}

// ---- 供应商对账单 ----
const stmtVisible = ref(false)
const stmtLoading = ref(false)
const statement = ref(null)
const stmt = reactive({ supplierId: '', from: null, to: null })

async function loadStatement() {
  if (!stmt.supplierId) {
    ElMessage.warning('请输入供应商ID')
    return
  }
  stmtLoading.value = true
  try {
    const res = await getStatement({
      supplierId: stmt.supplierId,
      from: stmt.from || undefined,
      to: stmt.to || undefined
    })
    statement.value = res.data
  } finally {
    stmtLoading.value = false
  }
}

async function onExportStatement() {
  const blob = await exportStatement({
    supplierId: stmt.supplierId,
    from: stmt.from || undefined,
    to: stmt.to || undefined
  })
  saveBlob(blob, `statement-${stmt.supplierId}.xlsx`)
}

onMounted(reload)
</script>

<style scoped>
.toolbar { margin-bottom: 12px; display: flex; gap: 8px; }
.pager { margin-top: 16px; justify-content: flex-end; display: flex; }
</style>
