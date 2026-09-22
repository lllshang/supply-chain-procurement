<template>
  <el-card>
    <CategoryTree
      ref="treeRef"
      :fetcher="getBudgetSubjectTree"
      :invalidate-api="invalidateBudgetSubject"
      :level-max="3"
      write-perm="budget:subject:write"
      @add="onAdd"
      @edit="onEdit"
    />

    <el-dialog v-model="visible" :title="form.id ? '编辑预算科目' : '新增预算科目'" width="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="父科目">
          <el-input :model-value="parentLabel" disabled />
        </el-form-item>
        <el-form-item label="科目编码" required>
          <el-input v-model="form.code" :disabled="!!form.id" placeholder="科目编码（唯一）" />
        </el-form-item>
        <el-form-item label="科目名称" required>
          <el-input v-model="form.name" placeholder="请输入科目名称" />
        </el-form-item>
        <el-form-item label="科目类型" required>
          <EnumSelect v-model="form.subjectType" enum-key="subjectType" :clearable="false" />
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
import EnumSelect from '@/components/EnumSelect.vue'
import { getBudgetSubjectTree, createBudgetSubject, updateBudgetSubject, invalidateBudgetSubject } from '@/api/budget'

// P-B1 预算科目：树 + 增改/置无效
const treeRef = ref()
const visible = ref(false)
const saving = ref(false)
const parentLabel = ref('根节点')
const form = reactive({ id: null, parentId: 0, code: '', name: '', subjectType: 1 })

function onAdd(payload) {
  Object.assign(form, { id: null, parentId: payload.parentId, code: '', name: '', subjectType: 1 })
  parentLabel.value = payload.parentName || '根节点'
  visible.value = true
}

function onEdit(node) {
  Object.assign(form, {
    id: node.id, parentId: node.parentId, code: node.code, name: node.name, subjectType: node.subjectType ?? 1
  })
  parentLabel.value = node.parentId === 0 ? '根节点' : '（当前父科目）'
  visible.value = true
}

async function onSave() {
  if (!form.code || !form.name || !form.subjectType) {
    ElMessage.warning('编码、名称、科目类型必填')
    return
  }
  saving.value = true
  try {
    const payload = { parentId: form.parentId, code: form.code, name: form.name, subjectType: form.subjectType }
    if (form.id) {
      await updateBudgetSubject(form.id, payload)
    } else {
      await createBudgetSubject(payload)
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
