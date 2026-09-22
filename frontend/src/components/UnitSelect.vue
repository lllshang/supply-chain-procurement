<template>
  <el-select
    :model-value="modelValue"
    filterable
    :clearable="clearable"
    :disabled="disabled"
    :placeholder="placeholder"
    style="width: 100%"
    @update:model-value="onChange"
  >
    <el-option
      v-for="u in units"
      :key="u.code"
      :label="`${u.name}（${u.code}）`"
      :value="u.code"
    />
  </el-select>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { listUnits } from '@/api/catalog'

// 计量单位下拉（unit.code），数据源 GET /api/v1/catalog/units/list
defineProps({
  modelValue: { default: null },
  clearable: { type: Boolean, default: true },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '请选择单位' }
})
const emit = defineEmits(['update:modelValue'])

const units = ref([])

function onChange(v) {
  emit('update:modelValue', v)
}

async function load() {
  try {
    const res = await listUnits()
    units.value = res?.data || []
  } catch (e) {
    units.value = []
  }
}

defineExpose({ reload: load })
onMounted(load)
</script>
