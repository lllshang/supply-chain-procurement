<template>
  <el-dialog v-model="visible" :title="form.id ? '编辑 SKU' : '新建 SKU'" width="560px">
    <el-form :model="form" label-width="90px">
      <el-form-item label="SKU编码" required>
        <el-input v-model="form.skuCode" placeholder="唯一编码" />
      </el-form-item>
      <el-form-item label="条码">
        <el-input v-model="form.barcode" placeholder="选填" />
      </el-form-item>
      <el-form-item label="规格">
        <el-input v-model="form.spec" placeholder="选填" />
      </el-form-item>
      <el-form-item label="基本单位" required>
        <UnitSelect v-model="form.baseUnit" />
      </el-form-item>
      <el-form-item label="采购单位">
        <UnitSelect v-model="form.purchaseUnit" />
      </el-form-item>
      <el-form-item label="计价方式">
        <EnumSelect v-model="form.valuationType" enum-key="valuationType" />
      </el-form-item>
      <el-form-item label="参考价">
        <MoneyInput v-model="form.referencePrice" />
      </el-form-item>
      <el-form-item label="标准价" :error="priceError">
        <MoneyInput v-model="form.standardPrice" @update:model-value="priceError = ''" />
      </el-form-item>
      <el-form-item label="主图">
        <FileUpload v-model:value="form.imageFileKey" accept="image/*" biz-type="sku" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import UnitSelect from '@/components/UnitSelect.vue'
import EnumSelect from '@/components/EnumSelect.vue'
import MoneyInput from '@/components/MoneyInput.vue'
import FileUpload from '@/components/FileUpload.vue'
import { createSku, updateSku, getSku, validatePrice } from '@/api/catalog'

// 产品库 · SKU 表单（含价格规则预检）
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  spuId: { type: [Number, String], default: null },
  sku: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const saving = ref(false)
const priceError = ref('')
const form = reactive({
  id: null,
  skuCode: '',
  barcode: '',
  spec: '',
  baseUnit: '',
  purchaseUnit: '',
  valuationType: 0,
  referencePrice: null,
  standardPrice: null,
  imageFileKey: ''
})

function reset() {
  Object.assign(form, {
    id: null, skuCode: '', barcode: '', spec: '', baseUnit: '', purchaseUnit: '',
    valuationType: 0, referencePrice: null, standardPrice: null, imageFileKey: ''
  })
  priceError.value = ''
}

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    reset()
    if (props.sku?.id) {
      try {
        const res = await getSku(props.sku.id)
        const d = res?.data || {}
        Object.assign(form, {
          id: d.id,
          skuCode: d.skuCode || '',
          barcode: d.barcode || '',
          spec: d.spec || '',
          baseUnit: d.baseUnit || '',
          purchaseUnit: d.purchaseUnit || '',
          valuationType: d.valuationType ?? 0,
          referencePrice: d.referencePrice ?? null,
          standardPrice: d.standardPrice ?? null,
          imageFileKey: d.imageFileKey || ''
        })
      } catch (e) {
        Object.assign(form, { id: props.sku.id, skuCode: props.sku.skuCode, baseUnit: props.sku.baseUnit })
      }
    }
  }
)

async function precheckPrice() {
  if (form.standardPrice == null || !props.spuId) return true
  try {
    await validatePrice({ refType: 1, refId: props.spuId, price: form.standardPrice })
    priceError.value = ''
    return true
  } catch (e) {
    priceError.value = '价格越界或违反价格规则'
    return false
  }
}

async function onSave() {
  if (!form.skuCode || !form.baseUnit) {
    ElMessage.warning('SKU编码与基本单位必填')
    return
  }
  const ok = await precheckPrice()
  if (!ok) return
  saving.value = true
  try {
    const payload = {
      spuId: props.spuId,
      skuCode: form.skuCode,
      barcode: form.barcode,
      spec: form.spec,
      baseUnit: form.baseUnit,
      purchaseUnit: form.purchaseUnit,
      valuationType: form.valuationType,
      referencePrice: form.referencePrice,
      standardPrice: form.standardPrice,
      imageFileKey: form.imageFileKey
    }
    if (form.id) {
      await updateSku(form.id, payload)
    } else {
      await createSku(payload)
    }
    ElMessage.success('保存成功')
    visible.value = false
    emit('saved')
  } catch (e) {
    // 错误由拦截器提示（价格越界亦在此兜底）
  } finally {
    saving.value = false
  }
}
</script>
