<template>
  <div class="file-upload">
    <el-upload
      :show-file-list="false"
      :auto-upload="false"
      :accept="accept"
      :on-change="onChange"
    >
      <el-button :loading="uploading" :icon="UploadFilled">选择文件上传</el-button>
    </el-upload>

    <span v-if="value" class="key">
      <el-tag type="success" size="small">{{ value }}</el-tag>
      <el-button link type="danger" size="small" @click="$emit('update:value', '')">清除</el-button>
    </span>
    <span v-else class="tip">未上传</span>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { uploadFile } from '@/api/catalog'

// 文件上传封装（主图 imageFileKey / 资质附件 fileKey）
// 依赖前置 R1 端点 POST /api/v1/files/upload（field=file，取 data.fileKey）
defineProps({
  value: { type: String, default: '' },
  accept: { type: String, default: '' },
  bizType: { type: String, default: '' }
})
const emit = defineEmits(['update:value'])

const uploading = ref(false)

async function onChange(uploadFileObj) {
  const raw = uploadFileObj?.raw
  if (!raw) return
  uploading.value = true
  try {
    const res = await uploadFile(raw)
    const fileKey = res?.data?.fileKey
    if (fileKey) {
      emit('update:value', fileKey)
      ElMessage.success('上传成功')
    }
  } catch (e) {
    // 存储不可用等业务错误由拦截器提示
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.file-upload { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.key { display: inline-flex; align-items: center; gap: 6px; }
.tip { color: #999; font-size: 12px; }
</style>
