<template>
  <el-card>
    <PageHead title="产品库">
      <el-button v-permission="writePerm" type="primary" :icon="Plus" @click="openSpuForm()">新建产品</el-button>
      <el-button :icon="Download" @click="exportVisible = true">导出</el-button>
    </PageHead>
    <div class="toolbar">
      <el-input v-model="query.keyword" class="filter" placeholder="SPU 编码 / 名称" clearable :prefix-icon="Search" @keyup.enter="reload" />
      <TreeSelect v-model="query.categoryId" class="filter" :fetcher="getCategoryTree" :leaf-only="false" placeholder="品类" />
      <EnumSelect v-model="query.status" class="filter-sm" enum-key="productStatusName" placeholder="状态" />
      <el-button type="primary" :icon="Search" @click="reload">查询</el-button>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="160" />
      <el-table-column prop="spuCode" label="SPU编码" width="140" />
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="categoryName" label="品类" width="140" />
      <el-table-column prop="baseUnit" label="基本单位" width="100" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <StatusTag :value="row.status" enum-key="productStatusName" />
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="170" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openSkus(row)">SKU</el-button>
          <el-button v-permission="writePerm" link type="primary" @click="openSpuForm(row)">编辑</el-button>
          <el-button v-permission="writePerm" link type="success" v-if="row.status === 'DISABLED'" @click="toggleSpu(row, true)">启用</el-button>
          <el-button v-permission="writePerm" link type="warning" v-else @click="toggleSpu(row, false)">停用</el-button>
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

    <!-- SPU 表单 -->
    <SpuForm v-model="spuFormVisible" :spu="editingSpu" @saved="reload" />

    <!-- SKU 抽屉 -->
    <el-drawer v-model="skuDrawerVisible" :title="`SKU 列表 - ${currentSpu?.name || ''}`" size="62%">
      <div class="drawer-toolbar">
        <el-button v-permission="writePerm" type="primary" :icon="Plus" @click="openSkuForm()">新建 SKU</el-button>
        <el-button :icon="Refresh" @click="loadSkus">刷新</el-button>
      </div>
      <el-table :data="skus" v-loading="skuLoading" stripe size="small">
        <el-table-column prop="id" label="ID" width="150" />
        <el-table-column prop="skuCode" label="SKU编码" width="140" />
        <el-table-column prop="spec" label="规格" />
        <el-table-column prop="baseUnit" label="基本单位" width="90" />
        <el-table-column prop="purchaseUnit" label="采购单位" width="90" />
        <el-table-column prop="standardPrice" label="标准价" width="100" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <StatusTag :value="row.status" enum-key="productStatusName" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openConversion(row)">单位换算</el-button>
            <el-button v-permission="writePerm" link type="primary" @click="openSkuForm(row)">编辑</el-button>
            <el-button v-permission="writePerm" link type="success" v-if="row.status === 'DISABLED'" @click="toggleSku(row, true)">启用</el-button>
            <el-button v-permission="writePerm" link type="warning" v-else @click="toggleSku(row, false)">停用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <!-- SKU 表单 -->
    <SkuForm v-model="skuFormVisible" :spu-id="currentSpu?.id" :sku="editingSku" @saved="loadSkus" />

    <!-- 单位换算面板 -->
    <UnitConversionPanel v-model="conversionVisible" :sku="conversionSku" />

    <!-- 导出对话框 -->
    <ExportDialog v-model="exportVisible" :query="query" />
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Plus, Search, Download, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '@/components/PageHead.vue'
import TreeSelect from '@/components/TreeSelect.vue'
import EnumSelect from '@/components/EnumSelect.vue'
import StatusTag from '@/components/StatusTag.vue'
import SpuForm from '@/components/catalog/SpuForm.vue'
import SkuForm from '@/components/catalog/SkuForm.vue'
import UnitConversionPanel from '@/components/catalog/UnitConversionPanel.vue'
import ExportDialog from '@/components/catalog/ExportDialog.vue'
import { getCategoryTree, pageSpus, listSkusBySpu, enableSpu, disableSpu, enableSku, disableSku } from '@/api/catalog'
import { usePagination } from '@/composables/usePagination'

// P-C5 产品库：SPU 列表 + SKU 抽屉 + 单位换算 + 导出
const writePerm = 'catalog:spu:write'

const query = reactive({ keyword: '', categoryId: null, status: null })
const list = ref([])
const { current, pageSize, total, loading, load, onCurrentChange } = usePagination((p) =>
  pageSpus({ ...query, ...p })
)

function reload() {
  return load().then((rows) => {
    list.value = rows
  })
}
function handlePage(p) {
  onCurrentChange(p).then((rows) => {
    list.value = rows
  })
}

// ---- SPU 表单 ----
const spuFormVisible = ref(false)
const editingSpu = ref(null)
function openSpuForm(row) {
  editingSpu.value = row || null
  spuFormVisible.value = true
}
async function toggleSpu(row, enable) {
  await (enable ? enableSpu(row.id) : disableSpu(row.id))
  ElMessage.success(enable ? '已启用' : '已停用')
  reload()
}

// ---- SKU 抽屉 ----
const skuDrawerVisible = ref(false)
const currentSpu = ref(null)
const skus = ref([])
const skuLoading = ref(false)
function openSkus(row) {
  currentSpu.value = row
  skuDrawerVisible.value = true
  loadSkus()
}
async function loadSkus() {
  if (!currentSpu.value) return
  skuLoading.value = true
  try {
    const res = await listSkusBySpu(currentSpu.value.id)
    skus.value = res?.data || []
  } catch (e) {
    skus.value = []
  } finally {
    skuLoading.value = false
  }
}

const skuFormVisible = ref(false)
const editingSku = ref(null)
function openSkuForm(row) {
  editingSku.value = row || null
  skuFormVisible.value = true
}
async function toggleSku(row, enable) {
  await (enable ? enableSku(row.id) : disableSku(row.id))
  ElMessage.success(enable ? '已启用' : '已停用')
  loadSkus()
}

// ---- 单位换算 ----
const conversionVisible = ref(false)
const conversionSku = ref(null)
function openConversion(row) {
  conversionSku.value = row
  conversionVisible.value = true
}

// ---- 导出 ----
const exportVisible = ref(false)

onMounted(reload)
</script>

<style scoped>
.toolbar { margin-bottom: 12px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.filter { width: 180px; --filter-width: 180px; }
.filter-sm { width: 120px; --filter-width: 120px; }
.spacer { flex: 1; }
.pager { margin-top: 16px; justify-content: flex-end; display: flex; }
.drawer-toolbar { margin-bottom: 12px; display: flex; gap: 8px; }
</style>
