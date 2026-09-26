<template>
  <div>
    <el-card>
      <PageHead title="合同台账">
        <el-button type="primary" :icon="Plus" v-permission="'contract:write'" @click="openCreate">合同登记</el-button>
      </PageHead>
      <el-table :data="rows" v-loading="loading" stripe :row-class-name="rowClassName">
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
        <el-table-column prop="owner" label="经办人" width="100" />
        <el-table-column prop="signDate" label="签订日期" width="110" />
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
        v-model:current-page="current"
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
          <!-- #33①：contractType 后端为 Integer，须提交数值（0=物料 / 1=服务），不能传枚举名 -->
          <EnumSelect v-model="form.contractType" enum-key="contractType" :clearable="false" />
        </el-form-item>
        <el-form-item label="合同金额" required><el-input-number v-model="form.amount" :min="0.01" :controls="false" style="width: 180px" /></el-form-item>
        <el-form-item label="预算科目">
          <el-select v-model="form.subjectId" placeholder="选填（S8：统计冗余）" clearable style="width: 180px">
            <el-option v-for="s in subjects" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="有效期">
          <el-date-picker v-model="form.validRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="生效日" end-placeholder="到期日" style="width: 100%" />
        </el-form-item>
        <el-form-item label="经办部门"><el-input v-model="form.ownerDept" placeholder="经办部门" /></el-form-item>
        <el-form-item label="经办人"><el-input v-model="form.owner" placeholder="经办人" /></el-form-item>
        <el-form-item label="签订日期">
          <el-date-picker v-model="form.signDate" type="date" value-format="YYYY-MM-DD" placeholder="签订日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="关联项目(选填)"><el-input v-model="form.projectId" placeholder="选填，关联项目ID" /></el-form-item>
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

    <!-- P4 R3a：补充签订 -->
    <el-dialog v-model="supplementVisible" title="补充签订（关联原合同，独立走审批）" width="520px" destroy-on-close>
      <el-form :model="supplementForm" label-width="100px">
        <el-form-item label="协议标题"><el-input v-model="supplementForm.title" placeholder="缺省：原合同名-补充协议" /></el-form-item>
        <el-form-item label="补充金额" required><el-input-number v-model="supplementForm.amount" :min="0.01" :controls="false" style="width: 180px" /></el-form-item>
        <el-form-item label="有效期" required>
          <el-date-picker v-model="supplementForm.validRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="生效日" end-placeholder="到期日" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="supplementForm.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="supplementVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSupplement">创建补充协议</el-button>
      </template>
    </el-dialog>

    <!-- P4 D15：SKU 白名单（无清单无定标合同的兜底供货范围；空=维持现网额度闸行为） -->
    <el-dialog v-model="whitelistVisible" title="SKU 白名单" width="640px" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon
        title="无价格清单且无定标的合同：下单 SKU 须在白名单内；白名单为空 = 维持现网额度闸兜底行为" />
      <el-table :data="whitelistRows" size="small" stripe>
        <el-table-column prop="skuId" label="SKU ID" width="200" />
        <el-table-column prop="remark" label="维护原因" min-width="180" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="danger" v-permission="'contract:write'" @click="removeWhitelistRow(row)">移除</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="白名单为空（维持现网行为）" :image-size="60" /></template>
      </el-table>
      <div class="whitelist-add">
        <el-input v-model="whitelistNew.skuId" placeholder="SKU ID" style="width: 200px" />
        <el-input v-model="whitelistNew.remark" placeholder="维护原因（审计）" style="width: 240px" />
        <el-button type="primary" v-permission="'contract:write'" @click="addWhitelistRow">添加</el-button>
      </div>
      <template #footer>
        <el-button @click="whitelistVisible = false">关闭</el-button>
        <el-button type="primary" :loading="saving" v-permission="'contract:write'" @click="saveWhitelist">保存白名单（仅影响后续下单）</el-button>
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
import { listBudgetSubjects } from '@/api/budget'
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
const form = reactive({ supplierId: '', awardId: '', title: '', contractType: 0, amount: 0, subjectId: null, validRange: null, ownerDept: '', owner: '', signDate: null, projectId: '' })
function openCreate() {
  Object.assign(form, { supplierId: '', awardId: '', title: '', contractType: 0, amount: 0, subjectId: null, validRange: null, ownerDept: '', owner: '', signDate: null, projectId: '' })
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
      subjectId: form.subjectId || null,
      amount: form.amount,
      validFrom: form.validRange[0],
      validTo: form.validRange[1],
      ownerDept: form.ownerDept || null,
      owner: form.owner || null,
      signDate: form.signDate || null,
      projectId: form.projectId ? Number(form.projectId) : null
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

// ---- P4 R3a：补充签订（关联原合同，独立走审批） ----
const supplementVisible = ref(false)
const supplementForm = reactive({ id: null, title: '', amount: 0, validRange: null, remark: '' })
function openSupplement(row) {
  Object.assign(supplementForm, { id: row.id, title: '', amount: 0, validRange: null, remark: '' })
  supplementVisible.value = true
}
async function onSupplement() {
  if (!supplementForm.amount || !supplementForm.validRange) {
    ElMessage.warning('请填写补充金额与有效期')
    return
  }
  saving.value = true
  try {
    await supplementContract(supplementForm.id, {
      title: supplementForm.title || null,
      amount: supplementForm.amount,
      validFrom: supplementForm.validRange[0],
      validTo: supplementForm.validRange[1],
      remark: supplementForm.remark || null
    })
    ElMessage.success('补充协议已创建，请提交审批')
    supplementVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

// ---- P4 D15：SKU 白名单（无清单无定标合同兜底供货范围） ----
const whitelistVisible = ref(false)
const whitelistRows = ref([])
const whitelistNew = reactive({ skuId: '', remark: '' })
let whitelistContractId = null
async function openWhitelist(row) {
  whitelistContractId = row.id
  whitelistNew.skuId = ''
  whitelistNew.remark = ''
  whitelistVisible.value = true
  try {
    const res = await listSkuWhitelist(row.id)
    whitelistRows.value = res.data || []
  } catch (e) {
    whitelistRows.value = []
  }
}
function addWhitelistRow() {
  // #28 教训：ID 全程字符串，不转 Number（后端 Long 反序列化）
  const skuId = String(whitelistNew.skuId || '').trim()
  if (!skuId || Number.isNaN(Number(skuId))) {
    ElMessage.warning('请填写数字 SKU ID')
    return
  }
  if (whitelistRows.value.some((r) => String(r.skuId) === skuId)) {
    ElMessage.warning('该 SKU 已在白名单中')
    return
  }
  whitelistRows.value.push({ skuId, remark: whitelistNew.remark || null })
  whitelistNew.skuId = ''
  whitelistNew.remark = ''
}
function removeWhitelistRow(row) {
  whitelistRows.value = whitelistRows.value.filter((r) => r !== row)
}
async function saveWhitelist() {
  saving.value = true
  try {
    await replaceSkuWhitelist(
      whitelistContractId,
      whitelistRows.value.map((r) => ({ skuId: r.skuId, remark: r.remark || null }))
    )
    ElMessage.success('白名单已保存（仅影响后续下单）')
    whitelistVisible.value = false
  } finally {
    saving.value = false
  }
}

// 预算科目（S8：统计冗余，选填）
const subjects = ref([])
async function loadSubjects() {
  try {
    const res = await listBudgetSubjects()
    subjects.value = res.data || []
  } catch (e) {
    subjects.value = []
  }
}

onMounted(() => { loadSubjects(); reload() })
</script>

<style scoped>
/* P4 D15：白名单维护行内新增区 */
.whitelist-add {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}
</style>
