<template>
  <div>
    <el-card>
      <PageHead title="到期预警">
        <el-button :icon="Search" @click="reload">刷新</el-button>
      </PageHead>
      <el-alert type="warning" :closable="false" style="margin-bottom: 12px">
        展示 valid_to 落在提前预警期（默认 30 天，app.contract.expire-warn-days 可配）内的生效合同。
      </el-alert>
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="contractId" label="合同 ID" width="200" />
        <el-table-column prop="contractNo" label="编号" width="170" />
        <el-table-column prop="title" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="supplierId" label="供应商" width="200" />
        <el-table-column prop="validTo" label="到期日" width="120" />
        <el-table-column prop="daysLeft" label="剩余天数" width="100" align="right">
          <template #default="{ row }">
            <el-tag :type="row.daysLeft <= 7 ? 'danger' : 'warning'" size="small">{{ row.daysLeft }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !rows.length" description="预警期内无到期合同" />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import PageHead from '@/components/PageHead.vue'
import { listExpiringContracts } from '@/api/purchase2'

const rows = ref([])
const loading = ref(false)

async function reload() {
  loading.value = true
  try {
    const res = await listExpiringContracts()
    rows.value = res.data || []
  } finally {
    loading.value = false
  }
}

onMounted(reload)
</script>
