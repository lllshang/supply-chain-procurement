<template>
  <div>
    <el-card>
      <PageHead title="服务考核">
        <el-button type="primary" :icon="Plus" v-permission="'order:assess:write'" @click="openCreate">登记考核</el-button>
      </PageHead>
      <div class="filter-row">
        <el-input v-model="queryOrderId" placeholder="按订单查询考核记录" clearable style="--filter-width: 220px" @keyup.enter="onQueryOrder" />
        <el-button :icon="Search" @click="onQueryOrder">查询</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="180" />
        <el-table-column prop="orderId" label="订单" width="200" />
        <el-table-column prop="assessDate" label="考核日期" width="120" />
        <el-table-column prop="score" label="评分" width="90" align="right" />
        <el-table-column prop="deductAmount" label="扣款金额" width="110" align="right" />
        <el-table-column prop="basis" label="考核依据" min-width="180" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
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
      <div class="note">注：仅服务订单（order_type=SERVICE）可考核；扣款金额供 P3 结算取数（D8）。</div>
    </el-card>

    <el-dialog v-model="formVisible" title="登记服务考核" width="560px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="订单 ID" required><el-input v-model="form.orderId" /></el-form-item>
        <el-form-item label="考核日期" required><el-date-picker v-model="form.assessDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="评分" required><el-input-number v-model="form.score" :min="0" :max="100" :controls="false" style="width: 160px" /></el-form-item>
        <el-form-item label="扣款金额"><el-input-number v-model="form.deductAmount" :min="0" :controls="false" style="width: 160px" /></el-form-item>
        <el-form-item label="考核依据" required><el-input v-model="form.basis" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import PageHead from '@/components/PageHead.vue'
import { usePagination } from '@/composables/usePagination'
import { pageAssesses, createAssess, listAssessByOrder } from '@/api/purchase2'

const queryOrderId = ref('')
const rows = ref([])
const { current, pageSize, total, loading, load, onCurrentChange } = usePagination((p) => pageAssesses(p))

function reload() {
  return load().then((data) => { rows.value = data })
}
function handlePage(p) {
  onCurrentChange(p).then((data) => { rows.value = data })
}

async function onQueryOrder() {
  if (!queryOrderId.value) {
    reload()
    return
  }
  loading.value = true
  try {
    const res = await listAssessByOrder(queryOrderId.value)
    rows.value = res.data || []
    total.value = rows.value.length
  } finally {
    loading.value = false
  }
}

const formVisible = ref(false)
const saving = ref(false)
const form = reactive({ orderId: '', assessDate: '', score: 100, deductAmount: 0, basis: '', remark: '' })
function openCreate() {
  Object.assign(form, { orderId: '', assessDate: '', score: 100, deductAmount: 0, basis: '', remark: '' })
  formVisible.value = true
}
async function onSave() {
  if (!form.orderId || !form.assessDate || !form.basis) {
    ElMessage.warning('请完整填写考核信息')
    return
  }
  saving.value = true
  try {
    await createAssess({
      orderId: form.orderId,
      assessDate: form.assessDate,
      score: form.score,
      deductAmount: form.deductAmount,
      basis: form.basis,
      remark: form.remark
    })
    ElMessage.success('考核已登记')
    formVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

onMounted(reload)
</script>

<style scoped>
.filter-row {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}
.note {
  margin-top: 10px;
  font-size: 12px;
  color: var(--app-text-weak, #999);
}
</style>
