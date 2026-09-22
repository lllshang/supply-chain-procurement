<template>
  <div class="category-tree">
    <PageHead :title="title">
      <el-button v-permission="writePerm" type="primary" :icon="Plus" @click="onAddRoot">新增一级</el-button>
      <el-button :icon="Refresh" @click="reload">刷新</el-button>
    </PageHead>

    <el-tree
      v-loading="loading"
      :data="treeData"
      node-key="id"
      :props="{ label: 'name', children: 'children' }"
      default-expand-all
      :expand-on-click-node="false"
      empty-text="暂无数据"
    >
      <template #default="{ data }">
        <span class="node">
          <span class="label">
            {{ data.name }}
            <el-tag v-if="data.status === 1" type="info" size="small" class="tag">已停用</el-tag>
          </span>
          <span class="ops">
            <el-button
              v-if="data.level < levelMax"
              v-permission="writePerm"
              link
              type="primary"
              size="small"
              @click.stop="onAddChild(data)"
            >新增子级</el-button>
            <el-button v-permission="writePerm" link type="primary" size="small" @click.stop="$emit('edit', data)">编辑</el-button>
            <el-button
              v-permission="writePerm"
              link
              type="danger"
              size="small"
              :disabled="data.status === 1"
              @click.stop="onInvalidate(data)"
            >置无效</el-button>
          </span>
        </span>
      </template>
    </el-tree>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHead from '@/components/PageHead.vue'

// 通用三级树（品类 / 供应商分类 / 预算科目复用）
const props = defineProps({
  // 拉树函数：() => R<CategoryTreeNodeVO[]>
  fetcher: { type: Function, required: true },
  // 置无效函数：(id) => R<Boolean>
  invalidateApi: { type: Function, default: null },
  levelMax: { type: Number, default: 3 },
  writePerm: { type: String, default: '' },
  // 页头标题（纯展示）：传入则在树工具栏上方渲染页头
  title: { type: String, default: '' }
})
const emit = defineEmits(['add', 'edit'])

const treeData = ref([])
const loading = ref(false)

async function reload() {
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

function onAddRoot() {
  emit('add', { parentId: 0, level: 1, parentName: '根节点' })
}

function onAddChild(data) {
  emit('add', { parentId: data.id, level: (data.level || 1) + 1, parentName: data.name })
}

async function onInvalidate(data) {
  try {
    await ElMessageBox.confirm(`确认将「${data.name}」置为无效？`, '提示', { type: 'warning' })
  } catch (e) {
    return
  }
  if (!props.invalidateApi) return
  try {
    await props.invalidateApi(data.id)
    ElMessage.success('已置为无效')
    reload()
  } catch (e) {
    // 被引用时后端拒绝，错误已由拦截器提示
  }
}

defineExpose({ reload })
onMounted(reload)
</script>

<style scoped>
.toolbar { margin-bottom: 12px; }
.node { display: flex; align-items: center; justify-content: space-between; width: 100%; padding-right: 8px; }
.label { display: inline-flex; align-items: center; gap: 6px; }
.tag { margin-left: 4px; }
.ops { opacity: 0.75; }
.ops:hover { opacity: 1; }
</style>
