<template>
  <div>
    <el-card>
      <PageHead title="价格库审核" />
      <div class="toolbar">
        <span class="toolbar-tip">异常价（超限价或低于历史均价 20%）进入待审队列，人工复核通过后沉淀为比价历史通过价。</span>
      </div>
      <!--
        待审列表（R-CST-01）：仅 PENDING 行可操作；通过 → approved=true 直接落库；
        驳回 → 弹窗必填 remark 后 approved=false。列取自 PriceHistory 实体。
      -->
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="skuId" label="SKU ID" width="100" />
        <el-table-column prop="supplierId" label="供应商ID" width="110" />
        <el-table-column prop="price" label="单价" width="120" align="right" />
        <el-table-column label="来源" width="100">
          <template #default="{ row }"><StatusTag :value="row.source" enum-key="priceSource" /></template>
        </el-table-column>
        <el-table-column label="来源单据" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ bizLabel(row.bizType, row.bizId) }}</template>
        </el-table-column>
        <el-table-column prop="effectiveDate" label="生效日期" width="120" />
        <el-table-column label="审核状态" width="100">
          <template #default="{ row }"><StatusTag :value="row.auditStatus" enum-key="priceAuditStatus" /></template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <template v-if="row.auditStatus === 'PENDING'">
              <el-button v-permission="'catalog:price:audit'" link type="success" @click="onApprove(row)">通过</el-button>
              <el-button v-permission="'catalog:price:audit'" link type="danger" @click="openReject(row)">驳回</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && rows.length === 0" description="暂无待审价" :image-size="80" />
    </el-card>

    <el-card style="margin-top: 16px">
      <template #header>
        <div class="panel-head">
          <span>近期通过价（审核参考比对）</span>
          <div class="panel-toolbar">
            <el-input v-model="recentSkuId" placeholder="SKU ID" clearable style="width: 140px" @keyup.enter="loadRecent" />
            <el-input-number v-model="recentLimit" :min="1" :max="20" :controls="false" style="width: 70px" />
            <el-button type="primary" :icon="Search" :loading="recentLoading" @click="loadRecent">查询</el-button>
          </div>
        </div>
      </template>
      <el-table v-if="recentRows.length" :data="recentRows" stripe size="small" max-height="320">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="skuId" label="SKU ID" width="100" />
        <el-table-column prop="supplierId" label="供应商ID" width="110" />
        <el-table-column prop="price" label="单价" width="120" align="right" />
        <el-table-column label="来源" width="100">
          <template #default="{ row }"><StatusTag :value="row.source" enum-key="priceSource" /></template>
        </el-table-column>
        <el-table-column label="来源单据" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ bizLabel(row.bizType, row.bizId) }}</template>
        </el-table-column>
        <el-table-column prop="effectiveDate" label="生效日期" width="120" />
        <el-table-column prop="auditAt" label="审核时间" width="180" />
      </el-table>
      <el-empty v-else description="输入 SKU ID 查询近期通过价" :image-size="80" />
    </el-card>

    <!-- 驳回（必填 remark 留痕） -->
    <el-dialog v-model="rejectVisible" title="驳回待审价" width="440px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="待审价">
          <span>SKU {{ rejectForm.skuId }} · 单价 {{ rejectForm.price }}</span>
        </el-form-item>
        <el-form-item label="驳回原因" required>
          <el-input v-model="rejectForm.remark" type="textarea" :rows="3" placeholder="必填，驳回原因将留痕" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="rejecting" @click="onReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import PageHead from '@/components/PageHead.vue'
import StatusTag from '@/components/StatusTag.vue'
import { enumLabel } from '@/constants/enums'
import { getPricePending, getPriceRecent, auditPrice } from '@/api/price'

// 来源单据类型（PriceHistory.bizType：QUOTATION/AWARD/ORDER）→ 可读标签 + 单据ID
const BIZ_TYPE_LABELS = { QUOTATION: '报价单', AWARD: '定标单', ORDER: '订单' }

function bizLabel(bizType, bizId) {
  if (!bizType && !bizId) return '-'
  const label = BIZ_TYPE_LABELS[bizType] || bizType || ''
  return bizId ? `${label}#${bizId}` : label
}

// ---- 待审列表 ----
const rows = ref([])
const loading = ref(false)

async function reloadPending() {
  loading.value = true
  try {
    const res = await getPricePending()
    rows.value = res.data || []
  } catch (e) {
    rows.value = []
  } finally {
    loading.value = false
  }
}

// 通过：直接落库（无二次确认，误操作可在比价历史中人工订正）
async function onApprove(row) {
  await auditPrice(row.id, { approved: true, remark: row.remark || '' })
  ElMessage.success(`SKU ${row.skuId} 待审价已通过`)
  reloadPending()
}

// 驳回：弹窗必填 remark
const rejectVisible = ref(false)
const rejecting = ref(false)
const rejectForm = reactive({ id: null, skuId: '', price: '', remark: '' })

function openReject(row) {
  Object.assign(rejectForm, { id: row.id, skuId: row.skuId, price: row.price, remark: '' })
  rejectVisible.value = true
}

async function onReject() {
  if (!rejectForm.remark || !rejectForm.remark.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  rejecting.value = true
  try {
    await auditPrice(rejectForm.id, { approved: false, remark: rejectForm.remark.trim() })
    ElMessage.success(`SKU ${rejectForm.skuId} 待审价已驳回`)
    rejectVisible.value = false
    reloadPending()
  } finally {
    rejecting.value = false
  }
}

// ---- 近期通过价面板 ----
const recentSkuId = ref('')
const recentLimit = ref(5)
const recentLoading = ref(false)
const recentRows = ref([])

// SKU ID 为 19 位雪花 ID（后端 Long→String 序列化，#28），全程保持字符串；
// 查询前仅做纯数字格式校验，严禁 Number()/parseInt() 转换（精度丢失教训）。
async function loadRecent() {
  const skuId = recentSkuId.value.trim()
  if (!/^\d+$/.test(skuId)) {
    ElMessage.warning('请输入有效的SKU ID')
    return
  }
  recentLoading.value = true
  try {
    const res = await getPriceRecent(skuId, recentLimit.value || 5)
    recentRows.value = res.data || []
    if (!recentRows.value.length) {
      ElMessage.info('该 SKU 暂无近期通过价')
    }
  } finally {
    recentLoading.value = false
  }
}

onMounted(reloadPending)
</script>

<style scoped>
.toolbar { margin-bottom: 12px; }
.toolbar-tip { color: var(--el-text-color-secondary); font-size: 13px; }
.panel-head { display: flex; align-items: center; justify-content: space-between; }
.panel-toolbar { display: flex; align-items: center; gap: 8px; }
</style>
