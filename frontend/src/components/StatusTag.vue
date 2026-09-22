<template>
  <el-tag v-if="shown" :type="tagType" size="small">{{ tagLabel }}</el-tag>
  <span v-else>-</span>
</template>

<script setup>
import { computed } from 'vue'
import { enumLabel, enumType } from '@/constants/enums'

// 状态标签：枚举值 → el-tag（颜色集中由 constants/enums.js 管理）
const props = defineProps({
  value: { default: null },
  enumKey: { type: String, default: null }
})

const shown = computed(
  () => props.value !== null && props.value !== undefined && props.value !== ''
)
const tagLabel = computed(() =>
  props.enumKey ? enumLabel(props.enumKey, props.value) : String(props.value)
)
const tagType = computed(() => (props.enumKey ? enumType(props.enumKey, props.value) : 'info'))
</script>
