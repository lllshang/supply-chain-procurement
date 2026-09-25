<template>
  <div>
    <el-card>
      <PageHead title="履约调整">
        <el-button type="primary" :icon="Plus" v-permission="'adjust:write'" @click="openCreate">新建调整</el-button>
      </PageHead>
      <el-table :data="rows" v-loading="loading" stripe :row-class-name="rowClassName">
        <el-table-column prop="adjustNo" label="调整单号" width="190" />
        <el-table-column label="业务" width="90">
          <template #default="{ row }"><StatusTag :value="row.bizType" enum-key="adjustBizType" /></template>
        </el-table-column>
        <el-table-column prop="orderId" label="订单" width="200" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }"><StatusTag :value="row.adjustType" enum-key="adjustType" /></template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" enum-key="adjustStatus" /></template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" link type="warning" v-permission="'adjust:write'" @click="onSubmit(row)">提交</el-button>
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
      <div class="note">注：涉及金额 ≤ 合同金额 5% 免审直接生效；超阈值走 FULFILLMENT_ADJUST 审批。</div>
    </el-card>

    <el-dialog v-model="formVisible" title="新建履约调整" width="560px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="业务类型" required><EnumSelect v-model="form.bizType" enum-key="adjustBizType" :clearable="false" /></el-form-item>
        <el-form-item label="订单 ID" required><el-input v-model="form.orderId" /></el-form-item>
        <el-form-item label="到货单 ID"><el-input v-model="form.arrivalId" placeholder="业务类型为到货时填写" /></el-form-item>
        <el-form-item label="调整类型" required><EnumSelect v-model="form.adjustType" enum-key="adjustType" :clearable="false" /></el-form-item>
        <el-form-item label="涉及金额"><el-input-number v-model="form.amount" :min="0" :controls="false" style="width: 180px" /></el-form-item>
        <el-form-item label="原因" required><el-input v-model="form.reason" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// P4：审批中心来源单据跳转定位（route.query.bizId 行高亮）
import { useQueryLocate } from '@/composables/useQueryLocate'
const { rowClassName } = useQueryLocate()
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import PageHead from '@/components/PageHead.vue'
import StatusTag from '@/components/StatusTag.vue'
import EnumSelect from '@/components/EnumSelect.vue'
import { usePagination } from '@/composables/usePagination'
import { pageAdjusts, createAdjust, submitAdjust } from '@/api/purchase2'

const rows = ref([])
const { current, pageSize, total, loading, load, onCurrentChange } = usePagination((p) => pageAdjusts(p))

function reload() {
  return load().then((data) => { rows.value = data })
}
function handlePage(p) {
  onCurrentChange(p).then((data) => { rows.value = data })
}

const formVisible = ref(false)
const saving = ref(false)
const form = reactive({ bizType: 'ORDER', orderId: '', arrivalId: '', adjustType: 'DIFF', amount: 0, reason: '' })
function openCreate() {
  Object.assign(form, { bizType: 'ORDER', orderId: '', arrivalId: '', adjustType: 'DIFF', amount: 0, reason: '' })
  formVisible.value = true
}
async function onSave() {
  if (!form.orderId || !form.reason) {
    ElMessage.warning('请填写订单 ID 与调整原因')
    return
  }
  saving.value = true
  try {
    await createAdjust({
      bizType: form.bizType,
      orderId: form.orderId,
      arrivalId: form.arrivalId || null,
      adjustType: form.adjustType,
      amount: form.amount,
      reason: form.reason
    })
    ElMessage.success('调整单已创建')
    formVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

async function onSubmit(row) {
  await ElMessageBox.confirm('提交后按阈值判定免审生效或进入审批。确认提交？', '提交调整')
  await submitAdjust(row.id)
  ElMessage.success('已提交')
  reload()
}

onMounted(reload)
</script>

<style scoped>
.note {
  margin-top: 10px;
  font-size: 12px;
  color: var(--app-text-weak, #999);
}
</style>
