<template>
  <el-drawer v-model="visible" title="供应商档案" size="48%">
    <div v-loading="loading">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="名称">{{ detail.name }}</el-descriptions-item>
        <el-descriptions-item label="统一社会信用代码">{{ detail.creditCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="等级">{{ detail.level || '-' }}</el-descriptions-item>
        <el-descriptions-item label="法人">{{ detail.legalPerson || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ detail.contact || '-' }}</el-descriptions-item>
        <el-descriptions-item label="电话">{{ detail.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="开户行">{{ detail.bankName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="银行账号">{{ detail.bankAccount || '-' }}</el-descriptions-item>
        <el-descriptions-item label="经营范围">{{ detail.businessScope || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源">
          <StatusTag :value="detail.source" enum-key="supplierSource" />
        </el-descriptions-item>
        <el-descriptions-item label="合作状态">
          <StatusTag :value="detail.coopStatus" enum-key="coopStatus" />
        </el-descriptions-item>
      </el-descriptions>

      <!-- 准入资格（只读，本阶段仅展示） -->
      <div class="section-title">准入资格</div>
      <el-card shadow="never" class="admission" v-loading="admissionLoading">
        <div class="admission-head">
          <span>资格判定：</span>
          <el-tag :type="admission.qualified ? 'success' : 'danger'">
            {{ admission.qualified ? '合格' : '不合格' }}
          </el-tag>
          <span class="ml">资质有效期：</span>
          <StatusTag :value="admission.qualValidity" enum-key="qualValidity" />
        </div>
        <div v-if="(admission.reasons || []).length" class="reasons">
          <div class="reasons-title">不合格原因：</div>
          <ul>
            <li v-for="(r, i) in admission.reasons" :key="i">{{ r }}</li>
          </ul>
        </div>
        <div v-else class="ok">无阻断原因</div>
        <div class="note">本阶段仅展示，不做业务拦截（拦截点 P2/P3）。</div>
      </el-card>

      <!-- 操作 -->
      <div class="section-title">状态操作</div>
      <el-form label-width="90px" class="ops">
        <el-form-item label="合作状态">
          <EnumSelect v-model="coopStatus" enum-key="coopStatus" :clearable="false" />
          <el-button v-permission="writePerm" type="primary" class="ml" @click="onSaveCoop">保存</el-button>
        </el-form-item>
        <el-form-item label="黑名单">
          <el-switch v-model="blacklist" active-text="列入黑名单" @change="onToggleBlacklist" />
        </el-form-item>
      </el-form>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import EnumSelect from '@/components/EnumSelect.vue'
import StatusTag from '@/components/StatusTag.vue'
import { getSupplier, getSupplierAdmission, updateSupplierCoopStatus, markSupplierBlacklist } from '@/api/supplier'

// P-S2 供应商档案抽屉（含准入资格只读区块）
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  supplier: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'changed'])

const writePerm = 'supplier:write'

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const loading = ref(false)
const admissionLoading = ref(false)
const detail = reactive({})
const admission = reactive({ qualified: false, qualValidity: 'NONE', reasons: [] })
const coopStatus = ref('NORMAL')
const blacklist = ref(false)

async function load() {
  if (!props.supplier?.id) return
  const id = props.supplier.id
  loading.value = true
  try {
    const res = await getSupplier(id)
    Object.assign(detail, res?.data || {})
    coopStatus.value = detail.coopStatus ?? 'NORMAL'
    blacklist.value = detail.isBlacklist === 'YES'
  } catch (e) {
    Object.assign(detail, props.supplier)
  } finally {
    loading.value = false
  }
  admissionLoading.value = true
  try {
    const res = await getSupplierAdmission(id)
    Object.assign(admission, res?.data || {})
  } catch (e) {
    Object.assign(admission, { qualified: false, qualValidity: 'NONE', reasons: [] })
  } finally {
    admissionLoading.value = false
  }
}

async function onSaveCoop() {
  try {
    await updateSupplierCoopStatus(props.supplier.id, coopStatus.value)
    ElMessage.success('合作状态已更新')
    emit('changed')
    load()
  } catch (e) {
    // 错误由拦截器提示
  }
}

async function onToggleBlacklist(val) {
  try {
    await markSupplierBlacklist(props.supplier.id, val)
    ElMessage.success(val ? '已列入黑名单' : '已移出黑名单')
    emit('changed')
    load()
  } catch (e) {
    blacklist.value = !val
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) load()
  }
)
</script>

<style scoped>
.section-title { font-weight: 600; margin: 18px 0 10px; }
.admission-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.ml { margin-left: 8px; }
.reasons { margin-top: 8px; color: var(--el-color-danger, #f56c6c); }
.reasons-title { font-weight: 600; }
.reasons ul { margin: 4px 0 0; padding-left: 20px; }
.ok { margin-top: 8px; color: var(--el-color-success, #67c23a); }
.note { margin-top: 8px; color: var(--app-text-weak, #7b8797); font-size: 12px; }
.ops { margin-top: 8px; }
</style>
