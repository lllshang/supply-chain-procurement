<template>
  <el-card>
    <!-- 页头：标题 + 说明 + 操作（对齐原型 page-header） -->
    <div class="page-header">
      <div>
        <h2 class="page-title">产品库</h2>
        <p class="page-desc">
          维护物料和服务类采购项；单规格使用产品编号，多规格按组合生成 SKU，服务类不进入库存。
        </p>
      </div>
      <div class="header-actions">
        <el-button :icon="Download" @click="exportVisible = true">导出</el-button>
        <el-button :icon="Upload" @click="onImport">批量导入</el-button>
        <el-button v-permission="writePerm" type="primary" :icon="Plus" @click="openSpuForm()">新增产品</el-button>
      </div>
    </div>

    <!-- 筛选面板（标签在上，对齐原型 filter-panel：灰底+边框+圆角） -->
    <div class="filter-panel">
      <div class="filter-item filter-keyword">
        <span class="filter-label">关键词</span>
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="产品编号、SKU、名称或条码"
          @keyup.enter="reload"
        />
      </div>
      <div class="filter-item filter-item-type">
        <span class="filter-label">采购项类型</span>
        <EnumSelect v-model="query.itemType" enum-key="itemType" placeholder="全部类型" @change="reload" />
      </div>
      <div class="filter-item filter-category">
        <span class="filter-label">三级品类</span>
        <TreeSelect
          v-model="query.categoryId"
          :fetcher="getCategoryTree"
          show-level
          path-label
          placeholder="全部品类层级"
          @change="reload"
        />
      </div>
      <div class="filter-item filter-spec-mode">
        <span class="filter-label">规格类型</span>
        <el-select v-model="query.specMode" placeholder="全部规格" clearable @change="reload">
          <el-option label="单规格" value="single" />
          <el-option label="多规格" value="multiple" />
          <el-option v-if="mixedPackFeatureEnabled" label="固定混色箱" value="mixed" />
        </el-select>
      </div>
      <div class="filter-item filter-status">
        <span class="filter-label">状态</span>
        <EnumSelect v-model="query.status" enum-key="productStatusName" placeholder="全部状态" @change="reload" />
      </div>
      <div class="filter-actions">
        <el-button type="primary" :icon="Search" @click="reload">查询</el-button>
        <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
      </div>
    </div>

    <div class="data-table">
      <el-table :data="list" v-loading="loading" border stripe empty-text="暂无匹配产品">
      <!-- 产品信息（服务类以内联标签标识，对齐原型） -->
      <el-table-column label="产品信息" min-width="190">
        <template #default="{ row }">
          <div class="product-cell">
            <el-image v-if="row.imageUrl" :src="row.imageUrl" fit="cover" class="product-thumb" />
            <div v-else class="product-thumb product-thumb-ph">
              <el-icon><Picture /></el-icon>
            </div>
            <div class="product-main">
              <div class="product-title-row">
                <span class="product-name">{{ row.name }}</span>
                <el-tag v-if="row.itemType === 'SERVICE'" size="small" type="warning" effect="plain">服务类</el-tag>
              </div>
              <span class="product-code">SPU {{ row.spuCode }}</span>
            </div>
          </div>
        </template>
      </el-table-column>

      <!-- 三级品类（叶子品类名，绿色胶囊；悬停显示完整路径） -->
      <el-table-column label="三级品类" min-width="120">
        <template #default="{ row }">
          <span class="category-leaf" :title="row.categoryPath || ''">{{ row.categoryName || row.categoryPath || '—' }}</span>
        </template>
      </el-table-column>

      <!-- 规格：多规格显示规格数，单规格显示规格值 -->
      <el-table-column label="规格" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">
          <el-tag v-if="row.skuCount > 1" size="small" effect="plain" type="primary">{{ row.skuCount }} 个规格</el-tag>
          <div v-else class="spec-cell">
            <el-tag v-if="row.packType === 1" size="small" type="success" effect="plain">固定混色箱</el-tag>
            <span>{{ row.specification || '—' }}</span>
          </div>
        </template>
      </el-table-column>

      <!-- 采购 / 基本单位（单位显示中文名） -->
      <el-table-column label="采购 / 基本单位" width="160">
        <template #default="{ row }">
          <span v-if="row.skuCount > 1" class="muted">多规格</span>
          <div v-else-if="row.purchaseUnit" class="unit-cell">
            <div class="unit-line">
              <span class="unit-caption">{{ unitCaption(row) }}</span>
              <strong>{{ row.purchaseUnitName || row.purchaseUnit }}</strong>
            </div>
            <div v-if="showBaseLine(row)" class="unit-line">
              <span class="unit-caption">基本</span>
              <span>{{ row.baseUnitName || row.baseUnit }}</span>
            </div>
            <span v-if="row.unitConversion" class="unit-conv">{{ row.unitConversion }}</span>
          </div>
          <span v-else class="muted">{{ row.baseUnitName || row.baseUnit || '—' }}</span>
        </template>
      </el-table-column>

      <!-- 标准价 -->
      <el-table-column label="标准价" width="110" align="right">
        <template #default="{ row }">
          <span class="price-red">{{ formatPrice(row) }}</span>
        </template>
      </el-table-column>

      <!-- 状态 -->
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <StatusTag :value="row.status" enum-key="productStatusName" />
        </template>
      </el-table-column>

      <!-- 操作 -->
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button v-permission="writePerm" link type="primary" @click="openSpuForm(row)">编辑</el-button>
          <el-button v-permission="writePerm" link type="success" v-if="row.status === 'DISABLED'" @click="toggleSpu(row, true)">启用</el-button>
          <el-button v-permission="writePerm" link type="warning" v-else @click="toggleSpu(row, false)">停用</el-button>
        </template>
      </el-table-column>
    </el-table>
    </div>

    <div class="pagination-row">
      <span class="total-text">共 {{ total }} 个产品 / {{ totalSku }} 个规格项</span>
      <el-pagination
        class="pager"
        background
        layout="prev, pager, next"
        :total="total"
        v-model:current-page="current"
        :page-size="pageSize"
        @current-change="handlePage"
      />
    </div>

    <!-- SPU 表单 -->
    <SpuForm v-model="spuFormVisible" :spu="editingSpu" @saved="reload" />

    <!-- 产品详情抽屉（只读，对齐原型 ProductListView L3627-3900） -->
    <el-drawer v-model="detailVisible" :title="`${detailRow?.name || ''} · 详情`" size="62%" destroy-on-close>
      <template v-if="detailRow">
        <!-- 概要头 -->
        <div class="detail-summary">
          <el-image v-if="detailRow.imageUrl" :src="detailRow.imageUrl" fit="cover" class="detail-image" />
          <div v-else class="detail-image detail-image-ph"><el-icon><Picture /></el-icon></div>
          <div class="detail-heading">
            <div class="detail-title-row">
              <span class="detail-name">{{ detailRow.name }}</span>
              <StatusTag :value="detailRow.status" enum-key="productStatusName" />
            </div>
            <span class="detail-code">SPU {{ detailRow.spuCode }}</span>
          </div>
        </div>

        <!-- 多规格 -->
        <template v-if="detailRow.skuCount > 1">
          <div class="variant-summary">
            <span>SPU 编号</span>
            <strong>{{ detailRow.spuCode }}</strong>
            <el-tag size="small" effect="plain">{{ detailRow.skuCount }} 个规格</el-tag>
            <span class="variant-meta">启用 {{ enabledSkuCount }} 个 · 标准价 {{ priceRange }}</span>
          </div>
          <el-descriptions :column="3" border class="detail-desc">
            <el-descriptions-item label="采购项类型">{{ itemTypeText(detailRow.itemType) }}</el-descriptions-item>
            <el-descriptions-item label="规格模式">多规格</el-descriptions-item>
            <el-descriptions-item label="三级品类">{{ detailRow.categoryPath || '--' }}</el-descriptions-item>
            <el-descriptions-item label="计量方式">{{ measurementText() }}</el-descriptions-item>
            <el-descriptions-item label="采购单位">{{ detailRow.purchaseUnitName || detailRow.purchaseUnit || '--' }}</el-descriptions-item>
            <el-descriptions-item label="基本单位">{{ detailRow.baseUnitName || detailRow.baseUnit || '--' }}</el-descriptions-item>
            <el-descriptions-item label="税率">{{ detailRow.taxRate != null ? detailRow.taxRate + '%' : '--' }}</el-descriptions-item>
            <el-descriptions-item label="单位换算" :span="3">{{ detailRow.unitConversion || '--' }}</el-descriptions-item>
            <el-descriptions-item label="产品简介" :span="3">{{ detailRow.description || '--' }}</el-descriptions-item>
          </el-descriptions>
          <el-table :data="detailSkus" border size="small" class="detail-table">
            <el-table-column prop="skuCode" label="SKU 编号" width="150" />
            <el-table-column label="SKU 条码" width="140">
              <template #default="{ row }">{{ row.barcode || '--' }}</template>
            </el-table-column>
            <el-table-column prop="spec" label="规格组合" min-width="150" />
            <el-table-column label="采购 / 基本单位" width="190">
              <template #default="{ row }">{{ unitName(row) }}</template>
            </el-table-column>
            <el-table-column label="标准价" width="100" align="right">
              <template #default="{ row }">{{ fmtMoney(row.standardPrice) }}</template>
            </el-table-column>
            <el-table-column label="参考价" width="100" align="right">
              <template #default="{ row }">{{ fmtMoney(row.referencePrice) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }"><StatusTag :value="row.status" enum-key="productStatusName" /></template>
            </el-table-column>
          </el-table>
        </template>

        <!-- 单规格 / 固定混色箱 -->
        <el-descriptions v-else :column="2" border class="detail-desc">
          <el-descriptions-item label="产品编号">{{ detailRow.spuCode }}</el-descriptions-item>
          <el-descriptions-item label="采购项类型">{{ itemTypeText(detailRow.itemType) }}</el-descriptions-item>
          <el-descriptions-item label="规格模式">{{ specModeLabel }}</el-descriptions-item>
          <el-descriptions-item label="条码">
            {{ detailRow.itemType === 'SERVICE' ? '无库存条码' : (singleSku?.barcode || '--') }}
          </el-descriptions-item>
          <el-descriptions-item label="三级品类" :span="2">{{ detailRow.categoryPath || '--' }}</el-descriptions-item>
          <el-descriptions-item label="规格" :span="2">{{ detailRow.spec || singleSku?.spec || '--' }}</el-descriptions-item>
          <el-descriptions-item label="计量方式">{{ measurementText() }}</el-descriptions-item>
          <el-descriptions-item label="状态"><StatusTag :value="detailRow.status" enum-key="productStatusName" /></el-descriptions-item>
          <el-descriptions-item label="采购单位">{{ detailRow.purchaseUnitName || detailRow.purchaseUnit || '--' }}</el-descriptions-item>
          <el-descriptions-item label="基本单位">{{ detailRow.baseUnitName || detailRow.baseUnit || '--' }}</el-descriptions-item>
          <el-descriptions-item label="标准价">{{ fmtMoney(singleSku?.standardPrice) }}</el-descriptions-item>
          <el-descriptions-item label="参考价">{{ fmtMoney(singleSku?.referencePrice) }}</el-descriptions-item>
          <el-descriptions-item label="税率">{{ detailRow.taxRate != null ? detailRow.taxRate + '%' : '--' }}</el-descriptions-item>
          <el-descriptions-item v-if="detailRow.itemType !== 'SERVICE'" label="单位换算" :span="2">{{ detailRow.unitConversion || '--' }}</el-descriptions-item>
          <el-descriptions-item label="产品简介" :span="2">{{ detailRow.description || '--' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 固定混色箱子件 -->
        <template v-if="isMixedDetail && detailMixed.length">
          <div class="mixed-detail-title">固定混色箱明细</div>
          <el-table :data="detailMixed" border size="small" class="detail-table">
            <el-table-column prop="name" label="子件名称" min-width="150" />
            <el-table-column prop="skuCode" label="子件 SKU 编号" min-width="160" />
            <el-table-column prop="specification" label="子件规格" min-width="170" />
            <el-table-column label="每箱数量" width="110" align="center">
              <template #default="{ row }">{{ row.quantity }} {{ row.baseUnit || '件' }}</template>
            </el-table-column>
          </el-table>
        </template>
      </template>
    </el-drawer>

    <!-- 导出对话框 -->
    <ExportDialog v-model="exportVisible" :query="query" />
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Search, Download, Refresh, Upload, Picture } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import TreeSelect from '@/components/TreeSelect.vue'
import EnumSelect from '@/components/EnumSelect.vue'
import StatusTag from '@/components/StatusTag.vue'
import SpuForm from '@/components/catalog/SpuForm.vue'
import ExportDialog from '@/components/catalog/ExportDialog.vue'
import { getCategoryTree, pageSpus, getSpu, listSkusBySpu, enableSpu, disableSpu } from '@/api/catalog'
import { usePagination } from '@/composables/usePagination'
import { mixedPackFeatureEnabled } from '@/constants/enums'

// P-C5 产品库：SPU 列表 + SKU 抽屉 + 单位换算 + 导出（对齐原型 ProductListView）
const writePerm = 'catalog:spu:write'

const router = useRouter()
function onImport() {
  router.push('/catalog/import')
}

const query = reactive({ keyword: '', categoryId: null, status: null, itemType: null, specMode: null })
const list = ref([])
const { current, pageSize, total, totalSku, loading, load, onCurrentChange } = usePagination((p) =>
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
function resetFilters() {
  Object.assign(query, { keyword: '', categoryId: null, status: null, itemType: null, specMode: null })
  reload()
}

function money(v) {
  if (v == null) return null
  return '¥' + Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function formatPrice(row) {
  const m1 = money(row.standardPriceMin)
  const m2 = money(row.standardPriceMax)
  if (!m1 && !m2) return '—'
  if (m1 && m2 && m1 === m2) return m1
  if (m1 && m2) return `${m1}~${m2}`
  return m1 || m2
}

// 单位列：标题文案与「基本」行按原型条件渲染（服务类=计价；有换算=采购+基本行）
function unitCaption(row) {
  if (row.itemType === 'SERVICE') return '计价'
  return row.unitConversion ? '采购' : '采购/基本'
}
function showBaseLine(row) {
  return row.itemType !== 'SERVICE' && !!row.unitConversion
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

// ---- 产品详情抽屉（只读，对齐原型 L3627-3900） ----
const detailVisible = ref(false)
const detailRow = ref(null)
const detailSkus = ref([])
const detailMixed = ref([])
async function openDetail(row) {
  detailRow.value = row
  detailVisible.value = true
  await Promise.all([loadDetailSkus(row.id), loadDetailSpu(row.id)])
}
async function loadDetailSkus(spuId) {
  try {
    const res = await listSkusBySpu(spuId)
    detailSkus.value = res?.data || []
  } catch (e) {
    detailSkus.value = []
  }
}
async function loadDetailSpu(spuId) {
  detailMixed.value = []
  try {
    const res = await getSpu(spuId)
    const d = res?.data || {}
    if (d.mixedPackComponents) {
      try { detailMixed.value = JSON.parse(d.mixedPackComponents) } catch (e) { detailMixed.value = [] }
    }
  } catch (e) {
    detailMixed.value = []
  }
}
const singleSku = computed(() => (detailSkus.value.length === 1 ? detailSkus.value[0] : null))
const isMixedDetail = computed(() => detailRow.value?.packType === 1)
const specModeLabel = computed(() => {
  if (!detailRow.value) return '--'
  if (detailRow.value.packType === 1) return '固定混色箱'
  return detailSkus.value.length > 1 ? '多规格' : '单规格'
})
const enabledSkuCount = computed(() => detailSkus.value.filter(s => s.status === 'NORMAL').length)
const priceRange = computed(() => {
  const ps = detailSkus.value.map(s => s.standardPrice).filter(v => v != null).map(Number)
  if (!ps.length) return '--'
  const min = Math.min(...ps), max = Math.max(...ps)
  return min === max ? money(min) : `${money(min)}~${money(max)}`
})
function itemTypeText(t) {
  return t === 'SERVICE' ? '服务类' : '物料类'
}
function measurementText() {
  const vt = detailSkus.value[0]?.valuationType
  if (!vt) return '计件'
  return vt === 'BY_WEIGHT' ? '计重' : '计件'
}
function unitName(row) {
  const pu = row.purchaseUnit, bu = row.baseUnit
  if (pu && bu) return `${pu} / ${bu}`
  return pu || bu || '--'
}
function fmtMoney(v) {
  return v != null ? money(v) : '--'
}

// ---- 导出 ----
const exportVisible = ref(false)

onMounted(reload)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.page-title { margin: 0; font-size: 18px; font-weight: 600; }
.page-desc { margin: 4px 0 0; color: var(--el-text-color-secondary, #909399); font-size: 12px; }
.header-actions { display: flex; gap: 8px; flex-shrink: 0; }

.filter-panel { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 14px; padding: 16px; margin-bottom: 18px; background: #f7f9fc; border: 1px solid #e7edf5; border-radius: 8px; }
.filter-item { display: flex; min-width: 0; flex-direction: column; gap: 7px; }
.filter-label { padding-left: 2px; color: #596577; font-size: 12px; font-weight: 600; }
.filter-keyword { width: 250px; }
.filter-item-type { width: 135px; }
.filter-category { width: 230px; }
.filter-spec-mode { width: 140px; }
.filter-status { width: 150px; }
.filter-item :deep(.el-input), .filter-item :deep(.el-select) { width: 100%; }
.filter-actions { display: flex; gap: 8px; margin-left: auto; }

.product-cell { display: flex; align-items: center; gap: 10px; min-width: 0; }
.product-thumb { width: 38px; height: 38px; flex: 0 0 38px; border: 1px solid #e5eaf1; border-radius: 6px; overflow: hidden; background: #f4f6fa; }
.product-thumb-ph { display: flex; align-items: center; justify-content: center; color: #a3adba; font-size: 17px; }
.product-main { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.product-title-row { display: flex; align-items: center; gap: 7px; min-width: 0; }
.product-name { overflow: hidden; color: #1f2937; font-size: 14px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.product-code { color: #7c8899; font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace; font-size: 12px; }

.category-leaf { display: inline-block; max-width: 100%; padding: 2px 8px; overflow: hidden; color: #236b4e; font-size: 12px; font-weight: 600; line-height: 18px; text-overflow: ellipsis; white-space: nowrap; background: #e9f7f0; border-radius: 999px; }

.spec-cell { display: flex; align-items: center; gap: 6px; min-width: 0; }
.spec-cell span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.unit-cell { display: flex; flex-direction: column; align-items: stretch; gap: 6px; color: #334155; }
.unit-line { display: flex; align-items: center; justify-content: space-between; gap: 10px; min-width: 0; }
.unit-line strong { font-size: 13px; font-weight: 600; }
.unit-caption { color: #8a95a5; font-size: 11px; }
.unit-conv { padding: 2px 6px; color: #236b4e; font-size: 11px; background: #e9f7f0; border-radius: 4px; }

.price-red { color: #d4380d; font-weight: 600; }
.muted { color: #8a95a5; font-size: 12px; }

.data-table { width: 100%; overflow: hidden; border-radius: 8px; }

.pagination-row { display: flex; align-items: center; justify-content: space-between; margin-top: 16px; }
.total-text { color: #6b7280; font-size: 13px; }
.pager { justify-content: flex-end; }

.drawer-toolbar { margin-bottom: 12px; display: flex; gap: 8px; }

/* 产品详情抽屉（只读） */
.detail-summary { display: flex; align-items: center; gap: 14px; padding: 4px 0 16px; margin-bottom: 16px; border-bottom: 1px solid #eef2f7; }
.detail-image { width: 56px; height: 56px; flex: 0 0 56px; border: 1px solid #e5eaf1; border-radius: 8px; overflow: hidden; background: #f4f6fa; }
.detail-image-ph { display: flex; align-items: center; justify-content: center; color: #a3adba; font-size: 22px; }
.detail-heading { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.detail-title-row { display: flex; align-items: center; gap: 8px; }
.detail-name { font-size: 16px; font-weight: 600; color: #1f2937; }
.detail-code { color: #7c8899; font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace; font-size: 12px; }
.variant-summary { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; color: #475569; font-size: 13px; }
.variant-summary strong { color: #1f2937; }
.variant-meta { margin-left: auto; color: #6b7280; }
.detail-desc { margin-bottom: 16px; }
.detail-table { margin-top: 4px; }
.mixed-detail-title { margin: 14px 0 8px; font-size: 14px; font-weight: 600; color: #1f2937; }
</style>
