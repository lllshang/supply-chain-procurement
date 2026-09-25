<template>
  <div>
    <el-card>
      <PageHead title="供应商对账单">
        <el-button v-permission="'payment:read'" :icon="Download" :disabled="!statement" @click="onExport">导出Excel</el-button>
      </PageHead>
      <div class="toolbar">
        <el-input
          v-model="query.supplierId"
          placeholder="供应商ID（必填）"
          clearable
          style="width: 180px"
          @keyup.enter="loadStatement"
        />
        <el-date-picker v-model="query.from" type="date" value-format="YYYY-MM-DD" placeholder="起始日期" style="width: 150px" />
        <el-date-picker v-model="query.to" type="date" value-format="YYYY-MM-DD" placeholder="截止日期" style="width: 150px" />
        <el-button type="primary" :icon="Search" :loading="loading" @click="loadStatement">查询</el-button>
      </div>

      <!-- 汇总：应付 / 已付 / 差额（字段名以 PaymentController/StatementVO 为准，兼容读法） -->
      <el-descriptions v-if="statement" :column="3" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="应付合计">{{ statement.totalPayable ?? statement.payableAmount ?? statement.payable }}</el-descriptions-item>
        <el-descriptions-item label="已付合计">{{ statement.totalPaid ?? statement.paidAmount ?? statement.paid }}</el-descriptions-item>
        <el-descriptions-item label="差额">{{ statement.balance ?? statement.balanceAmount }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else-if="!loading" description="输入供应商ID后查询对账单（期间可选）" :image-size="80" />

      <!--
        明细（R-PAY-02 AC③ 差额下钻）：「来源类型」可读列标明该行来自结算单还是付款单，
        「单号」即来源单据号（JS-… 结算 / FK-… 付款），差额可逐行追溯到具体来源单据。
      -->
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="date" label="日期" width="120" />
        <el-table-column prop="docNo" label="来源单据号" min-width="180" />
        <el-table-column label="来源类型" width="120">
          <template #default="{ row }">{{ directionLabel(row.direction) }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="140" align="right" />
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Download } from '@element-plus/icons-vue'
import PageHead from '@/components/PageHead.vue'
import { getStatement, exportStatement } from '@/api/settlement'
import { saveBlob } from '@/api/purchase2'

// 明细行 direction → 可读来源类型（后端 StatementVO.Row.direction：SETTLEMENT / PAYMENT）
const DIRECTION_LABELS = { SETTLEMENT: '结算单', PAYMENT: '付款单' }

function directionLabel(direction) {
  return DIRECTION_LABELS[direction] || direction || '-'
}

const loading = ref(false)
const statement = ref(null)
const query = reactive({ supplierId: '', from: null, to: null })

const rows = computed(() => (statement.value ? statement.value.rows || [] : []))

function buildParams() {
  return {
    supplierId: query.supplierId,
    from: query.from || undefined,
    to: query.to || undefined
  }
}

async function loadStatement() {
  if (!query.supplierId) {
    ElMessage.warning('请输入供应商ID')
    return
  }
  loading.value = true
  try {
    const res = await getStatement(buildParams())
    statement.value = res.data
  } finally {
    loading.value = false
  }
}

async function onExport() {
  const blob = await exportStatement(buildParams())
  saveBlob(blob, `statement-${query.supplierId}.xlsx`)
}
</script>

<style scoped>
.toolbar { margin-bottom: 12px; display: flex; gap: 8px; }
</style>
