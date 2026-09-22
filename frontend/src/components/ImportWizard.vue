<template>
  <div class="import-wizard">
    <el-steps :active="active" align-center finish-status="success" class="steps">
      <el-step title="上传文件" />
      <el-step title="预览校验" />
      <el-step title="导入结果" />
    </el-steps>

    <!-- 第一步：上传 -->
    <div v-if="active === 0" class="step-body">
      <el-upload
        drag
        :auto-upload="false"
        :limit="1"
        :accept="accept"
        :on-change="onFileChange"
        :on-remove="onFileRemove"
        :file-list="fileList"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或 <em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">{{ tip || `支持 ${accept} 格式` }}</div>
        </template>
      </el-upload>
      <div class="actions">
        <el-button type="primary" :loading="previewing" :disabled="!file" @click="doPreview">
          上传并预览
        </el-button>
        <slot name="extra" />
      </div>
    </div>

    <!-- 第二步：预览 -->
    <div v-else-if="active === 1" class="step-body">
      <el-alert
        :type="previewValid ? 'success' : 'error'"
        :closable="false"
        show-icon
        :title="previewValid ? '校验通过，可确认导入' : '校验未通过，请修正后重新上传'"
        class="mb"
      />
      <slot name="preview" :preview="preview">
        <el-empty description="无预览内容" :image-size="60" />
      </slot>
      <template v-if="preview && preview.errors && preview.errors.length">
        <div class="sub-title">错误定位（点击行可联动预览）</div>
        <ErrorTable :errors="preview.errors" @row-click="onErrorRow" />
      </template>
      <div class="actions">
        <el-button @click="reset">重新上传</el-button>
        <el-button type="primary" :disabled="!previewValid" :loading="importing" @click="doImport">
          确认导入
        </el-button>
      </div>
    </div>

    <!-- 第三步：结果 -->
    <div v-else class="step-body">
      <el-result :icon="resultIcon" :title="resultTitle" :sub-title="resultSubTitle" />
      <el-progress
        v-if="polling"
        :percentage="50"
        :indeterminate="true"
        :duration="3"
        :show-text="false"
      />
      <template v-if="taskData && taskData.errors && taskData.errors.length">
        <div class="sub-title">错误定位</div>
        <ErrorTable :errors="taskData.errors" />
      </template>
      <div class="actions center">
        <el-button type="primary" @click="reset">再导入一批</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import ErrorTable from '@/components/ErrorTable.vue'
import { useImportTask } from '@/composables/useImportTask'

// 通用「上传 → 预览 → 确认 → 进度」向导（产品导入 / 预算导入 / 批量绑定复用）
const props = defineProps({
  // 预览接口：(file) => R<预览VO>；批量绑定等无预览场景可留空（跳过预览）
  previewApi: { type: Function, default: null },
  // 提交接口：(file) => R<ImportTaskVO | BatchBindRespVO>
  submitApi: { type: Function, required: true },
  // 任务查询接口：(taskId) => R<ImportTaskVO>；同步接口（无 taskId）可留空
  taskApi: { type: Function, default: null },
  accept: { type: String, default: '.xlsx,.xls' },
  tip: { type: String, default: '' }
})
const emit = defineEmits(['preview', 'success', 'error', 'error-row'])

const active = ref(0)
const file = ref(null)
const fileList = ref([])
const preview = ref(null)
const previewing = ref(false)
const importing = ref(false)
const summary = ref(null)

const { running: polling, task: taskData, start: startTask, stop: stopTask } = useImportTask((taskId) =>
  props.taskApi(taskId)
)

const previewValid = computed(() => !(preview.value && preview.value.valid === false))

const resultIcon = computed(() => {
  if (polling.value) return 'info'
  const status = taskData.value?.status
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'error'
  if (summary.value) return summary.value.success === false ? 'warning' : 'success'
  return 'success'
})
const resultTitle = computed(() => {
  if (polling.value) return '导入进行中…'
  const status = taskData.value?.status
  if (status === 'SUCCESS') return '导入成功'
  if (status === 'FAILED') return '导入失败'
  return '处理完成'
})
const resultSubTitle = computed(() => {
  const t = taskData.value
  if (t && t.totalRows != null) {
    return `总行数 ${t.totalRows}，失败 ${t.errorRows ?? 0} 行`
  }
  const s = summary.value
  if (s) {
    return `总行数 ${s.totalRows ?? '-'}，成功 ${s.successRows ?? '-'}，失败 ${s.failRows ?? 0} 行`
  }
  return ''
})

function onFileChange(f) {
  file.value = f?.raw || null
  fileList.value = f ? [f] : []
}
function onFileRemove() {
  file.value = null
  fileList.value = []
}
function onErrorRow(row) {
  emit('error-row', row)
}

async function doPreview() {
  if (!file.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  if (!props.previewApi) {
    active.value = 1
    return
  }
  previewing.value = true
  try {
    const res = await props.previewApi(file.value)
    preview.value = res?.data || null
    emit('preview', preview.value)
    active.value = 1
  } catch (e) {
    // 错误由拦截器提示
  } finally {
    previewing.value = false
  }
}

async function doImport() {
  importing.value = true
  try {
    const res = await props.submitApi(file.value)
    const data = res?.data || null
    if (data && data.taskId && props.taskApi) {
      startTask(data.taskId)
    } else {
      summary.value = data
    }
    active.value = 2
    emit('success', data)
  } catch (e) {
    emit('error', e)
  } finally {
    importing.value = false
  }
}

function reset() {
  stopTask()
  active.value = 0
  file.value = null
  fileList.value = []
  preview.value = null
  summary.value = null
}

defineExpose({ reset })
</script>

<style scoped>
.steps { margin-bottom: 20px; }
.step-body { padding: 8px 4px; }
.actions { margin-top: 16px; display: flex; gap: 12px; }
.actions.center { justify-content: center; }
.sub-title { font-weight: 600; margin: 12px 0 8px; }
.mb { margin-bottom: 12px; }
</style>
