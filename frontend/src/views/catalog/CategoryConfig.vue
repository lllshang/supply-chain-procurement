<template>
  <div class="category-cascade" v-loading="loading">
    <!-- 一级分类 -->
    <section class="category-column">
      <div class="column-header">
        <span class="column-title">一级分类</span>
        <div class="column-actions">
          <el-tooltip content="刷新一级分类" placement="top">
            <el-button circle :icon="Refresh" @click="rootKeyword = ''" />
          </el-tooltip>
          <el-tooltip content="新增一级分类" placement="top">
            <el-button circle type="primary" :icon="Plus" @click="openCreate(1)" />
          </el-tooltip>
        </div>
      </div>

      <el-input
        v-model="rootKeyword"
        clearable
        :prefix-icon="Search"
        placeholder="分类名称"
        class="column-search"
      />

      <div class="column-table">
        <div class="column-table-header">
          <span>名称</span>
          <span class="align-center">
            商品数量
            <el-tooltip content="此处统计产品库中关联该分类及下级分类的商品数量" placement="top">
              <el-icon class="help-icon"><QuestionFilled /></el-icon>
            </el-tooltip>
          </span>
          <span class="align-center">编码</span>
          <span class="align-center">操作</span>
        </div>
        <div class="column-table-body">
          <div
            v-for="row in rootRows"
            :key="row.id"
            class="category-list-row"
            :class="{ 'is-selected': row.id === selectedRootId }"
            @click="selectRoot(row)"
          >
            <span class="row-name" :title="row.name">{{ row.name }}</span>
            <span class="row-count">{{ row.productCount }}</span>
            <span class="row-code">{{ row.displayCode }}</span>
            <span class="row-actions">
              <el-tooltip content="编辑" placement="top">
                <el-button class="row-action edit" :icon="EditPen" @click.stop="openEdit(row)" />
              </el-tooltip>
              <el-tooltip content="删除" placement="top">
                <el-button class="row-action delete" :icon="Delete" @click.stop="removeCategory(row)" />
              </el-tooltip>
            </span>
          </div>
          <div v-if="!rootRows.length" class="column-empty">未找到匹配分类</div>
          <div v-else class="column-end">已显示全部</div>
        </div>
      </div>
    </section>

    <!-- 二级分类 -->
    <section class="category-column">
      <div class="column-header">
        <span class="column-title" :title="secondColumnTitle">{{ secondColumnTitle }}</span>
        <div class="column-actions">
          <el-tooltip content="刷新二级分类" placement="top">
            <el-button circle :icon="Refresh" :disabled="!selectedRoot" @click="secondKeyword = ''" />
          </el-tooltip>
          <el-tooltip content="新增二级分类" placement="top">
            <el-button circle type="primary" :icon="Plus" :disabled="!selectedRoot" @click="openCreate(2)" />
          </el-tooltip>
        </div>
      </div>

      <el-input
        v-model="secondKeyword"
        clearable
        :prefix-icon="Search"
        placeholder="分类名称"
        class="column-search"
        :disabled="!selectedRoot"
      />

      <div class="column-table">
        <div class="column-table-header">
          <span>名称</span>
          <span class="align-center">
            商品数量
            <el-tooltip content="此处统计产品库中关联该分类及下级分类的商品数量" placement="top">
              <el-icon class="help-icon"><QuestionFilled /></el-icon>
            </el-tooltip>
          </span>
          <span class="align-center">编码</span>
          <span class="align-center">操作</span>
        </div>
        <div class="column-table-body">
          <template v-if="selectedRoot">
            <div
              v-for="row in secondRows"
              :key="row.id"
              class="category-list-row"
              :class="{ 'is-selected': row.id === selectedSecondId }"
              @click="selectSecond(row)"
            >
              <span class="row-name" :title="row.name">{{ row.name }}</span>
              <span class="row-count">{{ row.productCount }}</span>
              <span class="row-code">{{ row.displayCode }}</span>
              <span class="row-actions">
                <el-tooltip content="编辑" placement="top">
                  <el-button class="row-action edit" :icon="EditPen" @click.stop="openEdit(row)" />
                </el-tooltip>
                <el-tooltip content="删除" placement="top">
                  <el-button class="row-action delete" :icon="Delete" @click.stop="removeCategory(row)" />
                </el-tooltip>
              </span>
            </div>
            <div v-if="!secondRows.length" class="column-empty">当前一级分类暂无二级分类</div>
            <div v-else class="column-end">已显示全部</div>
          </template>
          <div v-else class="column-empty">请先选择一级分类</div>
        </div>
      </div>
    </section>

    <!-- 三级分类 -->
    <section class="category-column">
      <div class="column-header">
        <span class="column-title" :title="thirdColumnTitle">{{ thirdColumnTitle }}</span>
        <div class="column-actions">
          <el-tooltip content="刷新三级分类" placement="top">
            <el-button circle :icon="Refresh" :disabled="!selectedSecond" @click="thirdKeyword = ''" />
          </el-tooltip>
          <el-tooltip content="新增三级分类" placement="top">
            <el-button circle type="primary" :icon="Plus" :disabled="!selectedSecond" @click="openCreate(3)" />
          </el-tooltip>
        </div>
      </div>

      <el-input
        v-model="thirdKeyword"
        clearable
        :prefix-icon="Search"
        placeholder="分类名称"
        class="column-search"
        :disabled="!selectedSecond"
      />

      <div class="column-table">
        <div class="column-table-header">
          <span>名称</span>
          <span class="align-center">
            商品数量
            <el-tooltip content="此处统计产品库中关联该分类及下级分类的商品数量" placement="top">
              <el-icon class="help-icon"><QuestionFilled /></el-icon>
            </el-tooltip>
          </span>
          <span class="align-center">编码</span>
          <span class="align-center">操作</span>
        </div>
        <div class="column-table-body">
          <template v-if="selectedSecond">
            <div v-for="row in thirdRows" :key="row.id" class="category-list-row" @click="selectThird(row)">
              <span class="row-name" :title="row.name">{{ row.name }}</span>
              <span class="row-count">{{ row.productCount }}</span>
              <span class="row-code">{{ row.displayCode }}</span>
              <span class="row-actions">
                <el-tooltip content="编辑" placement="top">
                  <el-button class="row-action edit" :icon="EditPen" @click.stop="openEdit(row)" />
                </el-tooltip>
                <el-tooltip content="删除" placement="top">
                  <el-button class="row-action delete" :icon="Delete" @click.stop="removeCategory(row)" />
                </el-tooltip>
              </span>
            </div>
            <div v-if="!thirdRows.length" class="column-empty">当前二级分类暂无三级分类</div>
            <div v-else class="column-end">已显示全部</div>
          </template>
          <div v-else class="column-empty">请先选择二级分类</div>
        </div>
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" destroy-on-close>
      <el-form label-width="92px" @submit.prevent>
        <el-form-item label="上级分类">
          <el-input :model-value="parentPathText" disabled />
        </el-form-item>
        <el-form-item label="分类名称" required>
          <el-input
            v-model="categoryName"
            clearable
            placeholder="请输入分类名称"
            @keyup.enter="saveCategory"
          />
        </el-form-item>
        <el-form-item label="分类层级">
          <el-tag type="primary" effect="light">{{ creatingLevel }}级分类</el-tag>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCategory">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, EditPen, Plus, QuestionFilled, Refresh, Search } from '@element-plus/icons-vue'
import {
  getCategoryTree,
  getCategoryProductCounts,
  createCategory,
  updateCategory,
  invalidateCategory,
} from '@/api/catalog'

const tree = ref([])
const counts = ref({})
const loading = ref(false)

const rootKeyword = ref('')
const secondKeyword = ref('')
const thirdKeyword = ref('')
const selectedRootId = ref(null)
const selectedSecondId = ref(null)

function findNode(list, id) {
  for (const n of list || []) {
    if (String(n.id) === String(id)) return n
    if (n.children && n.children.length) {
      const f = findNode(n.children, id)
      if (f) return f
    }
  }
  return null
}

function pathOf(node) {
  const path = []
  const walk = (list, target) => {
    for (const n of list || []) {
      if (String(n.id) === String(target.id)) {
        path.unshift(n.name)
        return true
      }
      if (n.children && n.children.length && walk(n.children, target)) {
        path.unshift(n.name)
        return true
      }
    }
    return false
  }
  walk(tree.value, node)
  return path
}

function buildRows(nodes, keyword) {
  const kw = (keyword || '').trim().toLowerCase()
  return (nodes || [])
    .filter((n) => n.status == null || n.status === 0)
    .filter((n) => !kw || (n.name || '').toLowerCase().includes(kw))
    .map((n, i) => ({
      ...n,
      displayCode: String(i + 1).padStart(2, '0'),
      productCount: counts.value[n.id] || 0,
    }))
}

const rootRows = computed(() => buildRows(tree.value, rootKeyword.value))
const selectedRoot = computed(() =>
  tree.value.find((n) => String(n.id) === String(selectedRootId.value)) || null,
)
const secondRows = computed(() =>
  buildRows(selectedRoot.value ? selectedRoot.value.children : [], secondKeyword.value),
)
const selectedSecond = computed(() =>
  selectedRoot.value && selectedRoot.value.children
    ? selectedRoot.value.children.find((n) => String(n.id) === String(selectedSecondId.value)) || null
    : null,
)
const thirdRows = computed(() =>
  buildRows(selectedSecond.value ? selectedSecond.value.children : [], thirdKeyword.value),
)

const secondColumnTitle = computed(() =>
  selectedRoot.value ? `${selectedRoot.value.name}/二级分类` : '二级分类',
)
const thirdColumnTitle = computed(() =>
  selectedRoot.value && selectedSecond.value
    ? `${selectedRoot.value.name}/${selectedSecond.value.name}/三级分类`
    : '三级分类',
)

watch(
  rootRows,
  (rows) => {
    if (selectedRootId.value == null || !rows.some((r) => String(r.id) === String(selectedRootId.value))) {
      selectedRootId.value = rows.length ? rows[0].id : null
    }
  },
  { immediate: true },
)
watch(
  secondRows,
  (rows) => {
    if (selectedSecondId.value == null || !rows.some((r) => String(r.id) === String(selectedSecondId.value))) {
      selectedSecondId.value = rows.length ? rows[0].id : null
    }
  },
  { immediate: true },
)

function selectRoot(row) {
  if (selectedRootId.value === row.id) return
  selectedSecondId.value = null
  secondKeyword.value = ''
  thirdKeyword.value = ''
  selectedRootId.value = row.id
}
function selectSecond(row) {
  if (selectedSecondId.value === row.id) return
  selectedSecondId.value = row.id
  thirdKeyword.value = ''
}
function selectThird(row) {
  // 三级为叶子，仅用于查看/编辑/删除，无需联动
  void row
}

const dialogVisible = ref(false)
const dialogMode = ref('create')
const creatingLevel = ref(1)
const parentId = ref(0)
const categoryName = ref('')
const editingId = ref(null)
const saving = ref(false)

const dialogTitle = computed(() => `${dialogMode.value === 'create' ? '新增' : '编辑'}${creatingLevel.value}级分类`)
const parentPathText = computed(() => {
  if (!parentId.value) return '无，新增为一级分类'
  const node = findNode(tree.value, parentId.value)
  return node ? pathOf(node).join(' / ') : ''
})

function openCreate(level) {
  if (level === 2 && !selectedRoot.value) {
    ElMessage.warning('请先选择一级分类')
    return
  }
  if (level === 3 && !selectedSecond.value) {
    ElMessage.warning('请先选择二级分类')
    return
  }
  dialogMode.value = 'create'
  creatingLevel.value = level
  parentId.value = level === 1 ? 0 : level === 2 ? selectedRoot.value.id : selectedSecond.value.id
  categoryName.value = ''
  editingId.value = null
  dialogVisible.value = true
}

function openEdit(node) {
  dialogMode.value = 'edit'
  creatingLevel.value = pathOf(node).length
  parentId.value = node.parentId || 0
  categoryName.value = node.name
  editingId.value = node.id
  dialogVisible.value = true
}

function genCode(pid) {
  return 'CAT' + (pid || 0) + '-' + Date.now().toString(36).toUpperCase()
}

async function saveCategory() {
  const name = categoryName.value.trim()
  if (!name) {
    ElMessage.warning('请输入分类名称')
    return
  }
  // 同级重名校验
  const siblings =
    creatingLevel.value === 1
      ? tree.value
      : creatingLevel.value === 2
        ? selectedRoot.value.children
        : selectedSecond.value.children
  const dupNode = (siblings || []).find((s) =>
    String(s.id) !== String(editingId.value) && s.name === name,
  )
  if (dupNode) {
    ElMessage.warning('同级分类名称已存在')
    return
  }
  saving.value = true
  try {
    if (dialogMode.value === 'create') {
      await createCategory({ parentId: parentId.value, code: genCode(parentId.value), name })
      ElMessage.success('分类已新增')
    } else {
      const node = findNode(tree.value, editingId.value)
      await updateCategory(editingId.value, { parentId: parentId.value, code: node.code, name })
      ElMessage.success('分类已更新')
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    // 错误由拦截器提示
  } finally {
    saving.value = false
  }
}

async function removeCategory(node) {
  if (node.children && node.children.length) {
    ElMessage.warning('请先删除该分类下的所有下级分类')
    return
  }
  const cnt = counts.value[node.id] || 0
  if (cnt > 0) {
    ElMessage.warning(`该分类下还有 ${cnt} 个商品，不能删除`)
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除分类「${pathOf(node).join(' / ')}」吗？`, '删除分类', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch (e) {
    return
  }
  try {
    await invalidateCategory(node.id)
    if (selectedRootId.value === node.id) selectedRootId.value = null
    if (selectedSecondId.value === node.id) selectedSecondId.value = null
    ElMessage.success('分类已删除')
    await load()
  } catch (e) {
    // 被引用时后端拒绝
  }
}

async function load() {
  loading.value = true
  try {
    const [t, c] = await Promise.all([getCategoryTree(), getCategoryProductCounts()])
    tree.value = (t && t.data) || []
    counts.value = (c && c.data) || {}
  } catch (e) {
    tree.value = []
    counts.value = {}
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.category-cascade {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  min-height: 520px;
  overflow: hidden;
  border: 1px solid #e4eaf2;
  border-radius: 8px;
  background: #fff;
}

.category-column {
  display: flex;
  min-width: 0;
  flex-direction: column;
  background: #fff;
}

.category-column + .category-column {
  border-left: 1px solid #e6ebf2;
}

.column-header {
  display: flex;
  min-height: 52px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px 8px;
}

.column-title {
  min-width: 0;
  overflow: hidden;
  color: #303846;
  font-size: 15px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.column-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
}

.column-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.column-search {
  width: auto;
  margin: 0 14px 10px;
}

.column-table {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  border-top: 1px solid #edf1f6;
}

.column-table-header,
.category-list-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 82px 54px 68px;
  align-items: center;
}

.column-table-header {
  min-height: 42px;
  padding: 0 14px;
  color: #5f6b7a;
  background: #fafbfc;
  font-size: 12px;
  font-weight: 600;
}

.column-table-body {
  min-height: 0;
  flex: 1;
  overflow: auto;
}

.category-list-row {
  position: relative;
  min-height: 42px;
  padding: 0 14px;
  border-bottom: 1px solid #f0f3f7;
  color: #374151;
  cursor: pointer;
  transition: background-color 140ms ease, box-shadow 140ms ease;
}

.category-list-row:hover {
  background: #f7faff;
}

.category-list-row.is-selected {
  background: #edf5ff;
  box-shadow: inset 3px 0 #409eff;
}

.row-name {
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-count,
.row-code {
  color: #4b5563;
  font-size: 13px;
  text-align: center;
}

.align-center {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
}

.help-icon {
  color: #9aa6b5;
  font-size: 13px;
}

.row-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.row-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.row-action {
  width: 27px;
  height: 22px;
  padding: 0;
  border: 0;
}

.row-action.edit {
  color: #2979ff;
  background: #eaf3ff;
}

.row-action.edit:hover {
  color: #fff;
  background: #2979ff;
}

.row-action.delete {
  color: #dc2626;
  background: #fff0f0;
}

.row-action.delete:hover {
  color: #fff;
  background: #dc2626;
}

.column-empty {
  display: flex;
  min-height: 220px;
  align-items: center;
  justify-content: center;
  color: #9aa6b5;
  font-size: 13px;
}

.column-end {
  padding: 12px 0 14px;
  color: #9aa6b5;
  font-size: 12px;
  text-align: center;
}

@media (max-width: 1360px) {
  .column-table-header,
  .category-list-row {
    grid-template-columns: minmax(0, 1fr) 68px 46px 62px;
    padding-right: 10px;
    padding-left: 10px;
  }

  .column-header {
    padding-right: 10px;
    padding-left: 10px;
  }

  .column-search {
    margin-right: 10px;
    margin-left: 10px;
  }
}
</style>
