<template>
  <el-select
    :model-value="modelValue"
    :clearable="clearable"
    :placeholder="placeholder"
    :disabled="disabled"
    style="width: 100%"
    @update:model-value="onChange"
  >
    <el-option v-for="opt in options" :key="opt.value" :label="opt.label" :value="opt.value" />
  </el-select>
</template>

<script setup>
import { computed } from 'vue'
import { enumOptions } from '@/constants/enums'

// 枚举下拉：读取 constants/enums.js 本地常量表
const props = defineProps({
  enumKey: { type: String, required: true },
  modelValue: { default: null },
  clearable: { type: Boolean, default: true },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '请选择' }
})
const emit = defineEmits(['update:modelValue'])

const options = computed(() => enumOptions(props.enumKey))

function onChange(v) {
  emit('update:modelValue', v)
}
</script>
