<template>
  <el-card>
    <PageHead title="产品绑定">
      <el-button v-permission="writePerm" type="primary" :icon="Plus" :disabled="!supplierId" @click="openForm()">新增绑定</el-button>
      <el-button :icon="Upload" :disabled="!supplierId" @click="batchVisible = true">批量绑定</el-button>
      <el-button :icon="Refresh" :disabled="!supplierId" @click="loadBindings">刷新</el-button>
    </PageHead>
    <div class="toolbar">
      <el-select v-model="supplierId" filterable placeholder="选择供应商" style="width: 260px" @change="loadBindings">
        <el-option v-for="s in suppliers" :key="s.id" :label="s.name" :value="s.id" />
      </el-select>
    </div>

    <el-table :data="bindings" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="160" />
      <el-table-column prop="skuId" label="SKU ID" width="160" />
      <el-table-column prop="skuCode" label="SKU编码" width="140" />
      <el-table-column prop="supplierSkuCode" label="供应商货号" width="140" />
      <el-table-column prop="supplyPrice" label="供货价" width="110" />
      <el-table-column prop="packageUnit" label="包装单位" width="110" />
      <el-table-column label="绑定范围" width="130">
        <template #default="{ row }">{{ enumLabel('bindScope', row.bindScope) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="writePerm" link type="danger" @click="onUnbind(row)">解绑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增绑定 -->
    <el-dialog v-model="formVisible" title="新增绑定" width="480px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="SKU ID" required>
          <el-input-number v-model="form.skuId" :min="1" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="供应商货号"><el-input v-model="form.supplierSkuCode" /></el-form-item>
        <el-form-item label="供货价"><MoneyInput v-model="form.supplyPrice" /></el-form-item>
        <el-form-item label="包装单位"><UnitSelect v-model="form.packageUnit" /></el-form-item>
        <el-form-item label="绑定范围">
          <EnumSelect v-model="form.bindScope" enum-key="bindScope" :clearable="false" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onBind">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量绑定 -->
    <el-dialog v-model="batchVisible" title="批量绑定" width="620px">
      <ImportWizard
        v-if="batchVisible"
        :submit-api="submitBatch"
        accept=".xlsx,.xls"
        tip="列：供应商ID / SKU ID / 供应商货号 / 供货价 / 包装单位 / 绑定范围"
        @success="loadBindings"
      />
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Plus, Refresh, Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import EnumSelect from '@/components/EnumSelect.vue'
import MoneyInput from '@/components/MoneyInput.vue'
import UnitSelect from '@/components/UnitSelect.vue'
import ImportWizard from '@/components/ImportWizard.vue'
import PageHead from '@/components/PageHead.vue'
import { enumLabel } from '@/constants/enums'
import { pageSuppliers, listSupplierSkus, bindSupplierSku, unbindSupplierSku, batchBindSupplierSku } from '@/api/supplier'

// P-S3 供应商产品绑定（含批量）
const writePerm = 'supplier:write'

const suppliers = ref([])
const supplierId = ref(null)
const bindings = ref([])
const loading = ref(false)

async function loadSuppliers() {
  try {
    const res = await pageSuppliers({ current: 1, size: 200 })
    suppliers.value = res?.data?.records || []
  } catch (e) {
    suppliers.value = []
  }
}

async function loadBindings() {
  if (!supplierId.value) {
    bindings.value = []
    return
  }
  loading.value = true
  try {
    const res = await listSupplierSkus({ supplierId: supplierId.value })
    bindings.value = res?.data || []
  } catch (e) {
    bindings.value = []
  } finally {
    loading.value = false
  }
}

const formVisible = ref(false)
const saving = ref(false)
const form = reactive({ skuId: null, supplierSkuCode: '', supplyPrice: null, packageUnit: '', bindScope: 'UNLIMITED' })

function openForm() {
  Object.assign(form, { skuId: null, supplierSkuCode: '', supplyPrice: null, packageUnit: '', bindScope: 'UNLIMITED' })
  formVisible.value = true
}

async function onBind() {
  if (!form.skuId) {
    ElMessage.warning('SKU ID 必填')
    return
  }
  saving.value = true
  try {
    await bindSupplierSku({ supplierId: supplierId.value, ...form })
    ElMessage.success('绑定成功')
    formVisible.value = false
    loadBindings()
  } catch (e) {
    // 冲突/校验错误由拦截器提示
  } finally {
    saving.value = false
  }
}

async function onUnbind(row) {
  try {
    await ElMessageBox.confirm('确认解绑该供应商与 SKU？', '提示', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await unbindSupplierSku(row.id)
    ElMessage.success('已解绑')
    loadBindings()
  } catch (e) {
    // 错误由拦截器提示
  }
}

// 批量绑定（同步返回 BatchBindRespVO）
const batchVisible = ref(false)
function submitBatch(file) {
  return batchBindSupplierSku(file)
}

onMounted(async () => {
  await loadSuppliers()
  if (suppliers.value.length) {
    supplierId.value = suppliers.value[0].id
    loadBindings()
  }
})
</script>

<style scoped>
.toolbar { margin-bottom: 12px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
</style>
