<template>
  <el-dialog v-model="visible" title="导出产品" width="480px">
    <el-form :model="form" label-width="90px">
      <el-form-item label="品类">
        <TreeSelect v-model="form.categoryId" :fetcher="getCategoryTree" :leaf-only="false" placeholder="全部品类" />
      </el-form-item>
      <el-form-item label="状态">
        <EnumSelect v-model="form.status" enum-key="productStatus" placeholder="全部状态" />
      </el-form-item>
      <el-form-item label="关键字">
        <el-input v-model="form.keyword" placeholder="SPU编码 / 名称" />
      </el-form-item>
      <el-form-item label="导出列">
        <el-input v-model="form.columns" placeholder="逗号分隔，留空=默认列" />
      </el-form-item>
    </el-form>

    <el-progress v-if="running" :percentage="60" :indeterminate="true" :duration="3" :show-text="false" />
    <div v-if="task && task.status" class="state">
      任务状态：<StatusTag :value="task.status" enum-key="importTaskStatus" />
      <span v-if="task.fileName" class="file">（{{ task.fileName }}）</span>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" :loading="running" @click="onExport">开始导出</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import TreeSelect from '@/components/TreeSelect.vue'
import EnumSelect from '@/components/EnumSelect.vue'
import StatusTag from '@/components/StatusTag.vue'
import { getCategoryTree, exportSpus, exportTaskStatus, exportDownload } from '@/api/catalog'
import { useImportTask } from '@/composables/useImportTask'

// 产品库 · 导出（异步 task_id + 轮询 + blob 下载，带 Authorization）
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  query: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const form = reactive({ categoryId: null, status: null, keyword: '', columns: '' })
const { running, task, start } = useImportTask(exportTaskStatus)

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      Object.assign(form, {
        categoryId: props.query.categoryId ?? null,
        status: props.query.status ?? null,
        keyword: props.query.keyword || '',
        columns: ''
      })
      if (task.value) task.value = null
    }
  }
)

watch(
  () => task.value?.status,
  async (status) => {
    if (status === 'SUCCESS') {
      try {
        const blob = await exportDownload(task.value.taskId)
        saveBlob(blob, task.value.fileName || 'product-export.xlsx')
        ElMessage.success('导出完成，已开始下载')
      } catch (e) {
        // 下载失败由拦截器提示
      }
    }
  }
)

async function onExport() {
  try {
    const res = await exportSpus({
      categoryId: form.categoryId,
      status: form.status,
      keyword: form.keyword,
      columns: form.columns
    })
    const taskId = res?.data?.taskId
    if (taskId) {
      start(taskId)
    } else {
      ElMessage.warning('导出任务创建失败')
    }
  } catch (e) {
    // 错误由拦截器提示
  }
}

function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
</script>

<style scoped>
.state { margin-top: 12px; color: #666; font-size: 13px; }
.file { color: #999; }
</style>
