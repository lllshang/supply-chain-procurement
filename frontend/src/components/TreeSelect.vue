<template>
  <el-tree-select
    :model-value="modelValue"
    :data="treeData"
    :props="treeProps"
    :placeholder="placeholder"
    :clearable="clearable"
    :check-strictly="true"
    :render-after-expand="false"
    :default-expand-all="showLevel"
    :loading="loading"
    node-key="id"
    value-key="id"
    :style="{ width: 'var(--filter-width, 100%)' }"
    @update:model-value="onChange"
  >
    <!-- 产品品类下拉：节点前缀「N级」层级徽标（对齐原型 category-option） -->
    <template #default="{ data }">
      <span v-if="showLevel" class="category-option">
        <span class="category-option-level" :class="`level-${data.level}`">{{ data.level }}级</span>
        <span class="category-option-name">{{ data.name }}</span>
      </span>
      <span v-else>{{ data[props.labelField] }}</span>
    </template>
  </el-tree-select>
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
  labelField: { type: String, default: 'name' },
  // 显示「N级」层级徽标 + 默认展开全部（对齐原型产品品类下拉）
  showLevel: { type: Boolean, default: false },
  // 选中项显示完整路径 label（如 餐饮食材 / 米面粮油 / 大米），对齐原型
  pathLabel: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])

const treeData = ref([])
const loading = ref(false)

function isLeaf(node) {
  return !node.children || node.children.length === 0
}

const treeProps = computed(() => ({
  label: props.pathLabel ? 'pathLabel' : props.labelField,
  children: 'children',
  // leafOnly 时禁用非叶子节点，保证只能选末级
  disabled: props.leafOnly ? (data) => !isLeaf(data) : () => false
}))

function onChange(v) {
  emit('update:modelValue', v)
}

// 补 level（后端已给，缺失时按深度兜底）与完整路径 label
function decorate(nodes, ancestors) {
  return (nodes || []).map((n) => {
    const names = [...ancestors, n.name]
    const node = { ...n, pathLabel: names.join(' / ') }
    if (node.level == null) node.level = names.length
    if (n.children && n.children.length) node.children = decorate(n.children, names)
    return node
  })
}

async function load() {
  loading.value = true
  try {
    const res = await props.fetcher()
    const raw = res?.data || []
    treeData.value = props.pathLabel || props.showLevel ? decorate(raw, []) : raw
  } catch (e) {
    treeData.value = []
  } finally {
    loading.value = false
  }
}

defineExpose({ reload: load })
onMounted(load)
</script>

<style scoped>
.category-option {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.category-option-level {
  display: inline-flex;
  flex: 0 0 32px;
  align-items: center;
  justify-content: center;
  height: 20px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.category-option-level.level-1 {
  color: #2563eb;
  background: #eaf2ff;
}

.category-option-level.level-2 {
  color: #b45309;
  background: #fff3db;
}

.category-option-level.level-3 {
  color: #16835d;
  background: #e8f7f0;
}

.category-option-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
