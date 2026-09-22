<template>
  <div>
    <el-card>
      <PageHead title="入库台账" />
      <div class="filter-row">
        <el-input v-model="query.orderId" placeholder="订单 ID" clearable style="--filter-width: 200px" @keyup.enter="reload" />
        <el-input v-model="query.supplierId" placeholder="供应商 ID" clearable style="--filter-width: 200px" @keyup.enter="reload" />
        <el-date-picker v-model="query.range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="入库开始" end-placeholder="入库结束" style="width: 260px" />
        <el-button type="primary" :icon="Search" @click="reload">查询</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="180" />
        <el-table-column prop="arrivalId" label="到货单" width="200" />
        <el-table-column prop="skuId" label="SKU" width="200" />
        <el-table-column prop="qtyExpected" label="应收" width="100" align="right" />
        <el-table-column prop="qtyActual" label="实收" width="100" align="right" />
        <el-table-column prop="qtyStored" label="已入库" width="100" align="right" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :value="row.status" enum-key="arrivalStatus" /></template>
        </el-table-column>
        <el-table-column prop="storedAt" label="入库时间" width="180" />
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
      <div class="note">注：P2 仅记台账流水（Q8），库存结存归 P3 结算阶段。</div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import PageHead from '@/components/PageHead.vue'
import StatusTag from '@/components/StatusTag.vue'
import { usePagination } from '@/composables/usePagination'
import { pageArrivalLedger } from '@/api/purchase2'

const query = reactive({ orderId: '', supplierId: '', range: null })
const rows = ref([])
const { current, pageSize, total, loading, load, onCurrentChange } = usePagination((p) =>
  pageArrivalLedger({
    orderId: query.orderId || undefined,
    supplierId: query.supplierId || undefined,
    from: query.range ? query.range[0] : undefined,
    to: query.range ? query.range[1] : undefined,
    ...p
  })
)

function reload() {
  return load().then((data) => { rows.value = data })
}
function handlePage(p) {
  onCurrentChange(p).then((data) => { rows.value = data })
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
