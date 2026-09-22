<template>
  <el-tree-select
    :model-value="modelValue"
    :data="treeData"
    :props="treeProps"
    :placeholder="placeholder"
    :clearable="clearable"
    :check-strictly="true"
    :render-after-expand="false"
    :loading="loading"
    node-key="id"
    value-key="id"
    :style="{ width: 'var(--filter-width, 100%)' }"
    @update:model-value="onChange"
  />
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'

// el-tree-select 封装：多用于选择“叶子”节点（如 SPU→品类 仅选 level=3）
const props = defineProps({
  // 拉树函数：() => R<CategoryTreeNodeVO[]>
  fetcher: { type: Function, required: true },
  modelValue: { default: null },
  leafOnly: { type: Boolean, default: true },
  clearable: { type: Boolean, default: true },
  placeholder: { type: String, default: '请选择' },
  // 自定义 label 字段（默认 name）
  labelField: { type: String, default: 'name' }
})
const emit = defineEmits(['update:modelValue'])

const treeData = ref([])
const loading = ref(false)

function isLeaf(node) {
  return !node.children || node.children.length === 0
}

const treeProps = computed(() => ({
  label: props.labelField,
  children: 'children',
  // leafOnly 时禁用非叶子节点，保证只能选末级
  disabled: props.leafOnly ? (data) => !isLeaf(data) : () => false
}))

function onChange(v) {
  emit('update:modelValue', v)
}

async function load() {
  loading.value = true
  try {
    const res = await props.fetcher()
    treeData.value = res?.data || []
  } catch (e) {
    treeData.value = []
  } finally {
    loading.value = false
  }
}

defineExpose({ reload: load })
onMounted(load)
</script>
