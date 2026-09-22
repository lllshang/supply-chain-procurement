<template>
  <div>
    <el-card>
      <PageHead title="常购清单">
        <el-button type="primary" :icon="Plus" v-permission="'purchase:frequent:write'" @click="openAdd">新增常购</el-button>
      </PageHead>
      <div class="toolbar">
        <el-input-number v-model="deptId" :min="1" :controls="false" placeholder="部门ID" style="width: 140px" />
        <el-button type="primary" :icon="Search" @click="reload">查询</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="180" />
        <el-table-column prop="skuId" label="SKU ID" width="200" />
        <el-table-column prop="deptId" label="部门" width="120" />
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" v-permission="'purchase:frequent:write'" @click="goApply(row)">带入申请</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="addVisible" title="新增常购" width="480px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="SKU ID" required><el-input v-model="addForm.skuId" placeholder="SKU ID" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="addForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onAdd">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import PageHead from '@/components/PageHead.vue'
import { listFrequent } from '@/api/purchase2'

const router = useRouter()
const deptId = ref(null)
const rows = ref([])
const loading = ref(false)

async function reload() {
  loading.value = true
  try {
    const res = await listFrequent(deptId.value)
    rows.value = res.data || []
  } finally {
    loading.value = false
  }
}

const addVisible = ref(false)
const saving = ref(false)
const addForm = reactive({ skuId: '', remark: '' })
function openAdd() {
  Object.assign(addForm, { skuId: '', remark: '' })
  addVisible.value = true
}
async function onAdd() {
  if (!addForm.skuId) {
    ElMessage.warning('请填写 SKU ID')
    return
  }
  saving.value = true
  try {
    await frequentBringIn({ skuIds: [Number(addForm.skuId)] })
    ElMessage.success('已加入常购清单')
    addVisible.value = false
    reload()
  } finally {
    saving.value = false
  }
}

// 带入申请页（由申请页通过「常购带入」按钮完成明细填充）
function goApply() {
  router.push('/purchase/apply')
}

onMounted(reload)
</script>
