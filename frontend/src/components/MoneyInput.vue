<template>
  <el-input-number
    :model-value="modelValue"
    :precision="2"
    :min="min"
    :max="max"
    :step="step"
    :controls="controls"
    :disabled="disabled"
    :placeholder="placeholder"
    style="width: 100%"
    @update:model-value="onChange"
  />
</template>

<script setup>
// 金额输入：DECIMAL(18,2)，默认 min=0、两位小数
defineProps({
  modelValue: { default: null },
  min: { type: Number, default: 0 },
  max: { type: Number, default: undefined },
  step: { type: Number, default: 0.01 },
  controls: { type: Boolean, default: true },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

function onChange(v) {
  emit('update:modelValue', v)
}
</script>
