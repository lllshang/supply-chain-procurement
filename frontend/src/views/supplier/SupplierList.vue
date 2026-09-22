<template>
  <el-card>
    <PageHead title="供应商档案">
      <el-button v-permission="writePerm" type="primary" :icon="Plus" @click="openForm()">新建供应商</el-button>
    </PageHead>
    <div class="toolbar">
      <el-input v-model="query.name" class="filter" placeholder="供应商名称" clearable :prefix-icon="Search" @keyup.enter="reload" />
      <TreeSelect v-model="query.categoryId" class="filter" :fetcher="getSupplierCategoryTree" :leaf-only="false" placeholder="供应商分类" />
      <EnumSelect v-model="query.coopStatus" class="filter-sm" enum-key="coopStatus" placeholder="合作状态" />
      <EnumSelect v-model="query.blacklist" class="filter-sm" enum-key="blacklist" placeholder="黑名单" />
      <el-button type="primary" :icon="Search" @click="reload">查询</el-button>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="150" />
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="creditCode" label="信用代码" width="180" />
      <el-table-column prop="level" label="等级" width="80" />
      <el-table-column prop="categoryName" label="分类" width="130" />
      <el-table-column label="合作状态" width="100">
        <template #default="{ row }"><StatusTag :value="row.coopStatus" enum-key="coopStatus" /></template>
      </el-table-column>
      <el-table-column label="黑名单" width="90">
        <template #default="{ row }"><StatusTag :value="row.isBlacklist" enum-key="blacklist" /></template>
      </el-table-column>
      <el-table-column label="来源" width="100">
        <template #default="{ row }"><StatusTag :value="row.source" enum-key="supplierSource" /></template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDrawer(row)">档案</el-button>
          <el-button v-permission="writePerm" link type="primary" @click="openForm(row)">编辑</el-button>
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

    <!-- 新增/编辑 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑供应商' : '新建供应商'" width="560px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="信用代码"><el-input v-model="form.creditCode" /></el-form-item>
        <el-form-item label="等级"><el-input v-model="form.level" placeholder="如 A/B/C" /></el-form-item>
        <el-form-item label="供应商分类">
          <TreeSelect v-model="form.supplierCategoryId" :fetcher="getSupplierCategoryTree" :leaf-only="false" placeholder="选择分类" />
        </el-form-item>
        <el-form-item label="法人"><el-input v-model="form.legalPerson" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="开户行"><el-input v-model="form.bankName" /></el-form-item>
        <el-form-item label="银行账号"><el-input v-model="form.bankAccount" /></el-form-item>
        <el-form-item label="经营范围"><el-input v-model="form.businessScope" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="来源">
          <EnumSelect v-model="form.source" enum-key="supplierSource" :clearable="false" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <SupplierDrawer v-model="drawerVisible" :supplier="drawerSupplier" @changed="reload" />
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import TreeSelect from '@/components/TreeSelect.vue'
import EnumSelect from '@/components/EnumSelect.vue'
import StatusTag from '@/components/StatusTag.vue'
import PageHead from '@/components/PageHead.vue'
import SupplierDrawer from '@/components/supplier/SupplierDrawer.vue'
import { getSupplierCategoryTree, pageSuppliers, createSupplier, updateSupplier, getSupplier } from '@/api/supplier'
import { usePagination } from '@/composables/usePagination'

// P-S2 供应商档案：列表 + 组合检索 + 档案抽屉 + 准入资格
const writePerm = 'supplier:write'

const query = reactive({ name: '', categoryId: null, coopStatus: null, blacklist: null })
const list = ref([])
const { current, pageSize, total, loading, load, onCurrentChange } = usePagination((p) =>
  pageSuppliers({ ...query, ...p })
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

// 表单
const formVisible = ref(false)
const saving = ref(false)
const form = reactive({
  id: null, name: '', creditCode: '', level: '', supplierCategoryId: null,
  legalPerson: '', contact: '', phone: '', bankName: '', bankAccount: '', businessScope: '', source: 0
})

function resetForm() {
  Object.assign(form, {
    id: null, name: '', creditCode: '', level: '', supplierCategoryId: null,
    legalPerson: '', contact: '', phone: '', bankName: '', bankAccount: '', businessScope: '', source: 0
  })
}

async function openForm(row) {
  resetForm()
  if (row) {
    try {
      const res = await getSupplier(row.id)
      Object.assign(form, res?.data || {}, { id: row.id })
    } catch (e) {
      Object.assign(form, { id: row.id, name: row.name, creditCode: row.creditCode, level: row.level, supplierCategoryId: row.supplierCategoryId })
    }
  }
  formVisible.value = true
}

async function onSave() {
  if (!form.name) {
    ElMessage.warning('供应商名称必填')
    return
  }
  saving.value = true
  try {
    const payload = { ...form }
    if (form.id) {
      await updateSupplier(form.id, payload)
    } else {
      await createSupplier(payload)
    }
    ElMessage.success('保存成功')
    formVisible.value = false
    reload()
  } catch (e) {
    // 错误由拦截器提示
  } finally {
    saving.value = false
  }
}

// 抽屉
const drawerVisible = ref(false)
const drawerSupplier = ref(null)
function openDrawer(row) {
  drawerSupplier.value = row
  drawerVisible.value = true
}

onMounted(reload)
</script>

<style scoped>
.toolbar { margin-bottom: 12px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.filter { width: 180px; --filter-width: 180px; }
.filter-sm { width: 120px; --filter-width: 120px; }
.spacer { flex: 1; }
.pager { margin-top: 16px; justify-content: flex-end; display: flex; }
</style>
