<template>
  <el-dialog v-model="visible" title="资质审核" width="460px">
    <el-form :model="form" label-width="80px">
      <el-form-item label="资质">
        <el-input :model-value="qual?.qualName || qual?.type || ''" disabled />
      </el-form-item>
      <el-form-item label="结论" required>
        <el-radio-group v-model="form.approved">
          <el-radio :value="true">通过</el-radio>
          <el-radio :value="false">驳回</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="意见" :required="form.approved === false">
        <el-input v-model="form.comment" type="textarea" :rows="3" :placeholder="form.approved === false ? '驳回必填原因' : '选填'" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="onConfirm">提交审核结果</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'

// P-S4 资质审核弹窗：通过/驳回 + 意见（驳回必填）
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  qual: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'confirm'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const form = reactive({ approved: true, comment: '' })

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      Object.assign(form, { approved: true, comment: '' })
    }
  }
)

function onConfirm() {
  if (form.approved === false && !form.comment) {
    ElMessage.warning('驳回必须填写原因')
    return
  }
  emit('confirm', { approved: form.approved, comment: form.comment })
}
</script>
