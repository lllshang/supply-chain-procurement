<template>
  <div>
    <el-card>
      <PageHead title="到货验收">
        <el-button type="primary" :icon="Plus" v-permission="'arrival:write'" @click="openCreate">到货登记</el-button>
      </PageHead>
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="arrivalNo" label="到货单号" width="200" />
        <el-table-column prop="orderId" label="订单" width="200" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" enum-key="arrivalStatus" /></template>
        </el-table-column>
        <el-table-column prop="arrivalDate" label="到货日期" width="120" />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openItems(row)">明细处理</el-button>
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

    <!-- 登记 -->
    <el-dialog v-model="formVisible" title="到货登记（应收 = 未入库余量）" width="480px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="订单 ID" required><el-input v-model="form.orderId" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">登记</el-button>
      </template>
    </el-dialog>

    <!-- 明细处理 -->
    <el-drawer v-model="itemsVisible" title="到货明细（分次入库 / 差异处理）" size="58%">
      <el-table :data="itemRows" stripe size="small">
        <el-table-column prop="skuId" label="SKU" width="200" />
        <el-table-column prop="qtyExpected" label="应收" width="90" align="right" />
        <el-table-column prop="qtyActual" label="实收" width="90" align="right" />
        <el-table-column prop="qtyStored" label="已入库" width="90" align="right" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" enum-key="arrivalStatus" /></template>
        </el-table-column>
        <el-table-column label="入库" width="200">
          <template #default="{ row }">
            <el-input-number v-model="row._storeQty" :min="0.01" :controls="false" size="small" style="width: 90px" />
            <el-button size="small" type="primary" link v-permission="'arrival:confirm'" @click="onStore(row)">确认</el-button>
          </template>
        </el-table-column>
        <el-table-column label="差异" width="170">
          <template #default="{ row }">
            <el-button size="small" link type="primary" v-permission="'arrival:write'" @click="onHandle(row, 'ACCEPT')">接受</el-button>
            <el-button size="small" link type="warning" v-permission="'arrival:write'" @click="onHandle(row, 'RETURN')">退货</el-button>
            <el-button size="small" link type="primary" v-permission="'arrival:write'" @click="onHandle(row, 'REPLENISH')">补货</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import PageHead from '@/components/PageHead.vue'
import StatusTag from '@/components/StatusTag.vue'
import { usePagination } from '@/composables/usePagination'
import { pageArrivals, createArrival, listArrivalItems, storeArrivalItem, handleArrivalItem } from '@/api/purchase2'

const rows = ref([])
const { current, pageSize, total, loading, load, onCurrentChange } = usePagination((p) => pageArrivals(p))

function reload() {
  return load().then((data) => { rows.value = data })
}
function handlePage(p) {
  onCurrentChange(p).then((data) => { rows.value = data })
}

// ---- 登记 ----
const formVisible = ref(false)
const saving = ref(false)
const form = reactive({ orderId: '', remark: '' })
function openCreate() {
  Object.assign(form, { orderId: '', remark: '' })
  formVisible.value = true
}
async function onSave() {
  if (!form.orderId) {
    ElMessage.warning('请填写订单 ID')
    return
  }
  saving.value = true
  try {
    await createArrival({ orderId: form.orderId, remark: form.remark })
    ElMessage.success('到货已登记')
    formVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

// ---- 明细处理 ----
const itemsVisible = ref(false)
const itemRows = ref([])
const currentArrival = ref(null)
async function openItems(row) {
  currentArrival.value = row
  const res = await listArrivalItems(row.id)
  itemRows.value = (res.data || []).map((i) => ({ ...i, _storeQty: 0 }))
  itemsVisible.value = true
}
async function onStore(row) {
  if (!row._storeQty) {
    ElMessage.warning('请填写本次入库数量')
    return
  }
  await storeArrivalItem(row.id, row._storeQty)
  ElMessage.success('已入库')
  openItems(currentArrival.value)
  reload()
}
async function onHandle(row, type) {
  await handleArrivalItem(row.id, type)
  ElMessage.success(type === 'ACCEPT' ? '已接受' : type === 'RETURN' ? '已退货（生成履约调整草稿）' : '已补货（生成履约调整草稿）')
  openItems(currentArrival.value)
}

onMounted(reload)
</script>
