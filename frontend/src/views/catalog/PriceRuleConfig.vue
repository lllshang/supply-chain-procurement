<template>
  <el-card>
    <div class="toolbar">
      <el-button v-permission="writePerm" type="primary" :icon="Plus" @click="openDialog()">新建规则</el-button>
      <el-button :icon="Check" @click="validateVisible = true">价格校验</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="160" />
      <el-table-column label="规则类型" width="120">
        <template #default="{ row }">{{ enumLabel('priceRuleType', row.ruleType) }}</template>
      </el-table-column>
      <el-table-column label="引用类型" width="120">
        <template #default="{ row }">{{ enumLabel('priceRefType', row.refType) }}</template>
      </el-table-column>
      <el-table-column prop="refId" label="引用ID" width="140" />
      <el-table-column prop="minPrice" label="最低价" width="120" />
      <el-table-column prop="maxPrice" label="最高价" width="120" />
      <el-table-column prop="expression" label="公式" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <StatusTag :value="row.status" enum-key="productStatus" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button v-permission="writePerm" link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button
            v-permission="writePerm"
            link
            type="danger"
            :disabled="row.status === 1"
            @click="onInvalidate(row)"
          >置无效</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑 -->
    <el-dialog v-model="visible" :title="form.id ? '编辑价格规则' : '新建价格规则'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="规则类型" required>
          <EnumSelect v-model="form.ruleType" enum-key="priceRuleType" />
        </el-form-item>
        <el-form-item label="引用类型" required>
          <EnumSelect v-model="form.refType" enum-key="priceRefType" />
        </el-form-item>
        <el-form-item label="引用对象" required>
          <TreeSelect
            v-if="form.refType === 2"
            v-model="form.refId"
            :fetcher="getCategoryTree"
            :leaf-only="false"
            placeholder="选择品类节点"
          />
          <el-input-number
            v-else
            v-model="form.refId"
            :min="1"
            :controls="false"
            placeholder="输入 SPU ID"
            style="width: 100%"
          />
        </el-form-item>
        <template v-if="form.ruleType === 4">
          <el-form-item label="公式表达式" required>
            <el-input v-model="form.expression" placeholder="如 base*1.1" />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="最低价">
            <MoneyInput v-model="form.minPrice" />
          </el-form-item>
          <el-form-item label="最高价">
            <MoneyInput v-model="form.maxPrice" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 价格校验（预检） -->
    <el-dialog v-model="validateVisible" title="价格规则校验（预检）" width="480px">
      <el-form :model="validateForm" label-width="90px">
        <el-form-item label="引用类型">
          <EnumSelect v-model="validateForm.refType" enum-key="priceRefType" />
        </el-form-item>
        <el-form-item label="引用ID">
          <el-input-number v-model="validateForm.refId" :min="1" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="价格">
          <MoneyInput v-model="validateForm.price" />
        </el-form-item>
      </el-form>
      <div class="preview-tip">预检结果仅供参考；最终以后端保存时校验为准。</div>
      <template #footer>
        <el-button @click="validateVisible = false">关闭</el-button>
        <el-button type="primary" :loading="validating" @click="onValidate">校验</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Plus, Check } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import EnumSelect from '@/components/EnumSelect.vue'
import MoneyInput from '@/components/MoneyInput.vue'
import TreeSelect from '@/components/TreeSelect.vue'
import StatusTag from '@/components/StatusTag.vue'
import { enumLabel } from '@/constants/enums'
import {
  listPriceRules,
  createPriceRule,
  updatePriceRule,
  invalidatePriceRule,
  validatePrice,
  getCategoryTree
} from '@/api/catalog'

// P-C4 价格规则：列表 + 增改/置无效 + 引用校验
const writePerm = 'catalog:price:write'

const list = ref([])
const loading = ref(false)

const visible = ref(false)
const saving = ref(false)
const form = reactive({ id: null, ruleType: 1, refType: 2, refId: null, minPrice: null, maxPrice: null, expression: '' })

const validateVisible = ref(false)
const validating = ref(false)
const validateForm = reactive({ refType: 2, refId: null, price: null })

async function reload() {
  loading.value = true
  try {
    const res = await listPriceRules()
    list.value = res?.data || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      ruleType: row.ruleType,
      refType: row.refType,
      refId: row.refId,
      minPrice: row.minPrice,
      maxPrice: row.maxPrice,
      expression: row.expression || ''
    })
  } else {
    Object.assign(form, { id: null, ruleType: 1, refType: 2, refId: null, minPrice: null, maxPrice: null, expression: '' })
  }
  visible.value = true
}

async function onSave() {
  if (!form.ruleType || !form.refType || !form.refId) {
    ElMessage.warning('规则类型、引用类型与引用对象必填')
    return
  }
  saving.value = true
  try {
    const payload = {
      ruleType: form.ruleType,
      refType: form.refType,
      refId: form.refId,
      minPrice: form.ruleType === 4 ? null : form.minPrice,
      maxPrice: form.ruleType === 4 ? null : form.maxPrice,
      expression: form.ruleType === 4 ? form.expression : null
    }
    if (form.id) {
      await updatePriceRule(form.id, payload)
    } else {
      await createPriceRule(payload)
    }
    ElMessage.success('保存成功')
    visible.value = false
    reload()
  } catch (e) {
    // 错误由拦截器提示
  } finally {
    saving.value = false
  }
}

async function onInvalidate(row) {
  try {
    await ElMessageBox.confirm('确认将该价格规则置为无效？', '提示', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await invalidatePriceRule(row.id)
    ElMessage.success('已置为无效')
    reload()
  } catch (e) {
    // 被引用时后端拒绝
  }
}

async function onValidate() {
  if (!validateForm.refType || !validateForm.refId || validateForm.price == null) {
    ElMessage.warning('请填写引用类型、引用ID与价格')
    return
  }
  validating.value = true
  try {
    await validatePrice({ refType: validateForm.refType, refId: validateForm.refId, price: validateForm.price })
    ElMessage.success('校验通过：价格在允许范围内')
  } catch (e) {
    // 越界/无规则等由拦截器提示
  } finally {
    validating.value = false
  }
}

onMounted(reload)
</script>

<style scoped>
.toolbar { margin-bottom: 12px; display: flex; gap: 12px; }
.preview-tip { color: var(--app-text-weak, #7b8797); font-size: 12px; margin: 4px 0 0 90px; }
</style>
