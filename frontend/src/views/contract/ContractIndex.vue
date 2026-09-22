<template>
  <div>
    <el-card>
      <PageHead title="合同台账">
        <el-button type="primary" :icon="Plus" v-permission="'contract:write'" @click="openCreate">合同登记</el-button>
      </PageHead>
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="no" label="合同编号" width="170" />
        <el-table-column prop="title" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="supplierId" label="供应商" width="200" />
        <el-table-column prop="amount" label="合同金额" width="110" align="right" />
        <el-table-column prop="availableAmount" label="可用额度" width="110" align="right" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.status" enum-key="contractStatus" /></template>
        </el-table-column>
        <el-table-column prop="validFrom" label="生效日" width="110" />
        <el-table-column prop="validTo" label="到期日" width="110" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button v-if="['DRAFT', 'REJECTED'].includes(row.status)" link type="warning" v-permission="'contract:submit'" @click="onSubmit(row)">提交</el-button>
            <el-button v-if="row.status === 'EFFECTIVE'" link type="danger" v-permission="'contract:terminate'" @click="onTerminate(row)">终止</el-button>
            <el-button v-if="['EFFECTIVE', 'EXPIRED'].includes(row.status)" link type="primary" v-permission="'contract:write'" @click="openRenew(row)">续签</el-button>
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

    <!-- 登记合同 -->
    <el-dialog v-model="formVisible" title="合同登记（来源定标时金额 = 该供应商份额）" width="600px" destroy-on-close>
      <el-form :model="form" label-width="110px">
        <el-form-item label="供应商 ID" required><el-input v-model="form.supplierId" /></el-form-item>
        <el-form-item label="来源定标 ID"><el-input v-model="form.awardId" placeholder="线下补录可空" /></el-form-item>
        <el-form-item label="合同名称" required><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="合同类型">
          <EnumSelect v-model="form.contractType" enum-key="itemType" :clearable="false" placeholder="0=物料 / 1=服务" />
        </el-form-item>
        <el-form-item label="合同金额" required><el-input-number v-model="form.amount" :min="0.01" :controls="false" style="width: 180px" /></el-form-item>
        <el-form-item label="有效期">
          <el-date-picker v-model="form.validRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="生效日" end-placeholder="到期日" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 续签 -->
    <el-dialog v-model="renewVisible" title="续签（新合同独立走审批）" width="520px" destroy-on-close>
      <el-form :model="renewForm" label-width="100px">
        <el-form-item label="新金额" required><el-input-number v-model="renewForm.amount" :min="0.01" :controls="false" style="width: 180px" /></el-form-item>
        <el-form-item label="新有效期">
          <el-date-picker v-model="renewForm.validRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="生效日" end-placeholder="到期日" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renewVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onRenew">续签</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import PageHead from '@/components/PageHead.vue'
import StatusTag from '@/components/StatusTag.vue'
import EnumSelect from '@/components/EnumSelect.vue'
import { usePagination } from '@/composables/usePagination'
import {
  pageContracts, createContract, submitContract, terminateContract, renewContract
} from '@/api/purchase2'

const rows = ref([])
const { current, pageSize, total, loading, load, onCurrentChange } = usePagination((p) => pageContracts(p))

function reload() {
  return load().then((data) => { rows.value = data })
}
function handlePage(p) {
  onCurrentChange(p).then((data) => { rows.value = data })
}

// ---- 登记 ----
const formVisible = ref(false)
const saving = ref(false)
const form = reactive({ supplierId: '', awardId: '', title: '', contractType: 'MATERIAL', amount: 0, validRange: null })
function openCreate() {
  Object.assign(form, { supplierId: '', awardId: '', title: '', contractType: 'MATERIAL', amount: 0, validRange: null })
  formVisible.value = true
}
async function onSave() {
  if (!form.supplierId || !form.title || !form.amount || !form.validRange) {
    ElMessage.warning('请完整填写登记信息')
    return
  }
  saving.value = true
  try {
    await createContract({
      supplierId: form.supplierId,
      awardId: form.awardId || null,
      title: form.title,
      contractType: form.contractType,
      amount: form.amount,
      validFrom: form.validRange[0],
      validTo: form.validRange[1]
    })
    ElMessage.success('合同已登记')
    formVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

async function onSubmit(row) {
  await ElMessageBox.confirm(`提交合同「${row.title}」进入审批（金额超阈值升两级）？`, '提交审批')
  await submitContract(row.id)
  ElMessage.success('已提交审批')
  reload()
}

async function onTerminate(row) {
  const { value } = await ElMessageBox.prompt('请填写终止原因', '终止合同')
  await terminateContract(row.id, value)
  ElMessage.success('已终止（额度冻结）')
  reload()
}

// ---- 续签 ----
const renewVisible = ref(false)
const renewForm = reactive({ id: null, amount: 0, validRange: null })
function openRenew(row) {
  Object.assign(renewForm, { id: row.id, amount: row.amount, validRange: null })
  renewVisible.value = true
}
async function onRenew() {
  if (!renewForm.validRange) {
    ElMessage.warning('请选择新有效期')
    return
  }
  saving.value = true
  try {
    await renewContract(renewForm.id, {
      amount: renewForm.amount,
      validFrom: renewForm.validRange[0],
      validTo: renewForm.validRange[1]
    })
    ElMessage.success('续签合同已创建，请提交审批')
    renewVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

onMounted(reload)
</script>
