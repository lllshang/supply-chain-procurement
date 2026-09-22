<template>
  <el-card>
    <CategoryTree
      ref="treeRef"
      :fetcher="getSupplierCategoryTree"
      :invalidate-api="invalidateSupplierCategory"
      :level-max="3"
      write-perm="supplier:write"
      @add="onAdd"
      @edit="onEdit"
    />

    <el-dialog v-model="visible" :title="form.id ? '编辑供应商分类' : '新增供应商分类'" width="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="父节点">
          <el-input :model-value="parentLabel" disabled />
        </el-form-item>
        <el-form-item label="层级">
          <el-input :model-value="`${form.level} 级`" disabled />
        </el-form-item>
        <el-form-item label="编码" required>
          <el-input v-model="form.code" :disabled="!!form.id" placeholder="分类编码（有效期内唯一）" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="请输入分类名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import CategoryTree from '@/components/CategoryTree.vue'
import {
  getSupplierCategoryTree,
  createSupplierCategory,
  updateSupplierCategory,
  invalidateSupplierCategory
} from '@/api/supplier'

// P-S1 供应商分类：三级树 + 增改/置无效
const treeRef = ref()
const visible = ref(false)
const saving = ref(false)
const parentLabel = ref('根节点')
const form = reactive({ id: null, parentId: 0, level: 1, code: '', name: '' })

function onAdd(payload) {
  Object.assign(form, { id: null, parentId: payload.parentId, level: payload.level, code: '', name: '' })
  parentLabel.value = payload.parentName || '根节点'
  visible.value = true
}

function onEdit(node) {
  Object.assign(form, { id: node.id, parentId: node.parentId, level: node.level, code: node.code, name: node.name })
  parentLabel.value = node.parentId === 0 ? '根节点' : '（当前父节点）'
  visible.value = true
}

async function onSave() {
  if (!form.code || !form.name) {
    ElMessage.warning('编码与名称必填')
    return
  }
  saving.value = true
  try {
    const payload = { parentId: form.parentId, code: form.code, name: form.name }
    if (form.id) {
      await updateSupplierCategory(form.id, payload)
    } else {
      await createSupplierCategory(payload)
    }
    ElMessage.success('保存成功')
    visible.value = false
    treeRef.value?.reload()
  } catch (e) {
    // 错误由拦截器提示
  } finally {
    saving.value = false
  }
}
</script>
