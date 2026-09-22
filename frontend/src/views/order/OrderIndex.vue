<template>
  <div>
    <el-card>
      <PageHead title="采购订单">
        <el-button type="primary" :icon="Plus" v-permission="'order:write'" @click="openCreate">下单</el-button>
      </PageHead>
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="orderNo" label="订单号" width="180" />
        <el-table-column prop="contractId" label="合同" width="200" />
        <el-table-column prop="applyId" label="来源申请" width="200" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }"><StatusTag :value="row.orderType" enum-key="itemType" /></template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="订单金额" width="110" align="right" />
        <el-table-column prop="budgetOccupied" label="预算占用" width="110" align="right" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" enum-key="orderStatus" /></template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openItems(row)">明细</el-button>
            <el-button link type="primary" @click="openTrace(row)">追溯</el-button>
            <el-button v-if="row.status === 'CREATED'" link type="warning" v-permission="'order:change'" @click="openChange(row)">变更</el-button>
            <el-button v-if="row.status === 'CREATED'" link type="danger" @click="onCancel(row)">取消</el-button>
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

    <!-- 下单（三重校验事务） -->
    <el-dialog v-model="formVisible" title="下单（三重校验：合同生效与额度 / 申请余量 / 换算快照）" width="860px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="合同 ID" required><el-input v-model="form.contractId" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="申请 ID" required><el-input v-model="form.applyId" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="明细行">
          <el-button size="small" :icon="Plus" @click="addItem">加行</el-button>
          <span class="hint">超额 / 超量将被拒绝且不落库；物料与服务自动拆单。</span>
        </el-form-item>
        <el-table :data="form.items" stripe size="small">
          <el-table-column label="申请明细ID" width="210">
            <template #default="{ row }"><el-input v-model="row.applyItemId" /></template>
          </el-table-column>
          <el-table-column label="SKU ID" width="200">
            <template #default="{ row }"><el-input v-model="row.skuId" /></template>
          </el-table-column>
          <el-table-column label="数量" width="110">
            <template #default="{ row }"><el-input-number v-model="row.qty" :min="0.01" :controls="false" style="width: 90px" /></template>
          </el-table-column>
          <el-table-column label="采购单位" width="110">
            <template #default="{ row }"><el-input v-model="row.purchaseUnit" placeholder="BOX" /></template>
          </el-table-column>
          <el-table-column label="单价(基本单位)" width="140">
            <template #default="{ row }"><el-input-number v-model="row.price" :min="0.01" :controls="false" style="width: 120px" /></template>
          </el-table-column>
          <el-table-column label="" width="60">
            <template #default="$index_scope"><el-button link type="danger" @click="form.items.splice($index_scope.$index, 1)">删</el-button></template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">提交下单</el-button>
      </template>
    </el-dialog>

    <!-- 变更 -->
    <el-dialog v-model="changeVisible" title="订单变更（仅已创建；免审留痕）" width="600px" destroy-on-close>
      <el-form :model="changeForm" label-width="110px">
        <el-form-item label="明细 ID" required><el-input v-model="changeForm.itemId" /></el-form-item>
        <el-form-item label="新数量" required><el-input-number v-model="changeForm.newQty" :min="0.01" :controls="false" style="width: 160px" /></el-form-item>
        <el-form-item label="变更原因" required><el-input v-model="changeForm.reason" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="changeVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onChange">提交变更</el-button>
      </template>
    </el-dialog>

    <!-- 明细 -->
    <el-drawer v-model="itemsVisible" title="订单明细（换算快照与来源追溯）" size="55%">
      <el-table :data="itemRows" stripe size="small">
        <el-table-column prop="skuId" label="SKU" width="200" />
        <el-table-column prop="qtyPurchase" label="采购数量" width="100" align="right" />
        <el-table-column prop="purchaseUnit" label="单位" width="90" />
        <el-table-column prop="qtyBase" label="基本数量" width="100" align="right" />
        <el-table-column prop="convRate" label="换算率" width="90" align="right" />
        <el-table-column prop="price" label="单价" width="100" align="right" />
        <el-table-column prop="applyItemId" label="来源明细" width="200" />
      </el-table>
    </el-drawer>

    <!-- 全链路追溯 -->
    <el-dialog v-model="traceVisible" title="全链路追溯" width="640px">
      <pre class="trace-pre">{{ traceText }}</pre>
    </el-dialog>
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
  pageOrders, createOrder, listOrderItems, getOrderTrace, changeOrder, cancelOrder
} from '@/api/purchase2'

const rows = ref([])
const { current, pageSize, total, loading, load, onCurrentChange } = usePagination((p) => pageOrders(p))

function reload() {
  return load().then((data) => { rows.value = data })
}
function handlePage(p) {
  onCurrentChange(p).then((data) => { rows.value = data })
}

// ---- 下单 ----
const formVisible = ref(false)
const saving = ref(false)
const form = reactive({ contractId: '', applyId: '', items: [] })

function emptyItem() {
  return { applyItemId: '', skuId: '', qty: 1, purchaseUnit: '', price: 0 }
}
function addItem() {
  form.items.push(emptyItem())
}
function openCreate() {
  Object.assign(form, { contractId: '', applyId: '', items: [emptyItem()] })
  formVisible.value = true
}
async function onSave() {
  const items = form.items.filter((i) => i.skuId).map((i) => ({
    applyItemId: i.applyItemId || null,
    skuId: i.skuId, qty: i.qty, purchaseUnit: i.purchaseUnit, price: i.price
  }))
  if (!form.contractId || !items.length) {
    ElMessage.warning('请填写合同 ID 与明细')
    return
  }
  saving.value = true
  try {
    const res = await createOrder({ contractId: form.contractId, applyId: form.applyId || null, items })
    ElMessage.success(`下单成功，生成 ${res.data.length} 张订单`)
    formVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

async function onCancel(row) {
  const { value } = await ElMessageBox.prompt('取消将释放合同额度并回冲申请余量，请填写原因', '取消订单')
  await cancelOrder(row.id, value)
  ElMessage.success('已取消')
  reload()
}

// ---- 变更 ----
const changeVisible = ref(false)
const changeForm = reactive({ orderId: null, itemId: '', newQty: 1, reason: '' })
function openChange(row) {
  Object.assign(changeForm, { orderId: row.id, itemId: '', newQty: 1, reason: '' })
  changeVisible.value = true
}
async function onChange() {
  if (!changeForm.itemId || !changeForm.reason) {
    ElMessage.warning('请填写明细 ID 与变更原因')
    return
  }
  saving.value = true
  try {
    await changeOrder(changeForm.orderId, {
      items: [{ itemId: changeForm.itemId, qty: changeForm.newQty }],
      reason: changeForm.reason
    })
    ElMessage.success('变更完成（留痕可查）')
    changeVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

// ---- 明细 / 追溯 ----
const itemsVisible = ref(false)
const itemRows = ref([])
async function openItems(row) {
  const res = await listOrderItems(row.id)
  itemRows.value = res.data || []
  itemsVisible.value = true
}

const traceVisible = ref(false)
const traceText = ref('')
async function openTrace(row) {
  const res = await getOrderTrace(row.id)
  traceText.value = JSON.stringify(res.data, null, 2)
  traceVisible.value = true
}

onMounted(reload)
</script>

<style scoped>
.hint {
  margin-left: 10px;
  font-size: 12px;
  color: var(--app-text-weak, #999);
}
.trace-pre {
  max-height: 420px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
