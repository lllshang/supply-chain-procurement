<template>
  <el-drawer v-model="visible" :title="`单位换算 - ${sku?.skuCode || ''}`" size="60%">
    <div class="toolbar">
      <el-button v-permission="writePerm" type="primary" :icon="Plus" @click="addVisible = true">新增换算</el-button>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border stripe size="small">
      <el-table-column prop="fromUnit" label="原单位" width="110" />
      <el-table-column prop="toUnit" label="目标单位" width="110" />
      <el-table-column prop="rate" label="换算率" width="110" />
      <el-table-column prop="version" label="版本" width="80" />
      <el-table-column prop="effectiveFrom" label="生效时间" min-width="170" />
      <el-table-column label="当前生效" width="110">
        <template #default="{ row }">
          <el-tag v-if="currentMap[row.fromUnit] === row.id" type="success" size="small">当前生效</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="addVisible" title="新增单位换算" width="460px" append-to-body>
      <el-form :model="form" label-width="90px">
        <el-form-item label="原单位" required>
          <UnitSelect v-model="form.fromUnit" />
        </el-form-item>
        <el-form-item label="目标单位" required>
          <UnitSelect v-model="form.toUnit" />
        </el-form-item>
        <el-form-item label="换算率" required>
          <el-input-number v-model="form.rate" :precision="6" :min="0.000001" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="生效时间">
          <el-date-picker
            v-model="form.effectiveFrom"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="留空=立即生效"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onAdd">保存</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import UnitSelect from '@/components/UnitSelect.vue'
import { listUnitConversions, currentUnitConversion, saveUnitConversion } from '@/api/catalog'

// 产品库 · 单位换算面板（版本管理 + 当前生效角标；前端不做换算计算）
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  sku: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue'])

const writePerm = 'catalog:unit:write'

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const list = ref([])
const loading = ref(false)
const currentMap = ref({})
const addVisible = ref(false)
const saving = ref(false)
const form = reactive({ fromUnit: '', toUnit: '', rate: null, effectiveFrom: null })

async function load() {
  if (!props.sku?.id) return
  loading.value = true
  try {
    const res = await listUnitConversions({ skuId: props.sku.id })
    list.value = res?.data || []
    const fromUnits = [...new Set(list.value.map((c) => c.fromUnit))]
    const map = {}
    await Promise.all(
      fromUnits.map(async (fu) => {
        try {
          const r = await currentUnitConversion({ skuId: props.sku.id, fromUnit: fu })
          map[fu] = r?.data?.id ?? null
        } catch (e) {
          map[fu] = null
        }
      })
    )
    currentMap.value = map
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function onAdd() {
  if (!form.fromUnit || !form.toUnit || form.rate == null) {
    ElMessage.warning('原单位、目标单位、换算率必填')
    return
  }
  if (form.fromUnit === form.toUnit) {
    ElMessage.warning('原单位与目标单位不可相同')
    return
  }
  saving.value = true
  try {
    await saveUnitConversion({
      skuId: props.sku.id,
      fromUnit: form.fromUnit,
      toUnit: form.toUnit,
      rate: form.rate,
      effectiveFrom: form.effectiveFrom || null
    })
    ElMessage.success('已新增换算（生成新版本）')
    addVisible.value = false
    Object.assign(form, { fromUnit: '', toUnit: '', rate: null, effectiveFrom: null })
    load()
  } catch (e) {
    // 错误由拦截器提示
  } finally {
    saving.value = false
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      Object.assign(form, { fromUnit: '', toUnit: '', rate: null, effectiveFrom: null })
      load()
    }
  }
)
</script>

<style scoped>
.toolbar { margin-bottom: 12px; display: flex; gap: 8px; }
</style>
