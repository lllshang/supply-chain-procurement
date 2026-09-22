<template>
  <div>
    <el-card>
      <PageHead title="采购申请">
        <el-button type="primary" :icon="Plus" @click="openDialog">新建采购申请</el-button>
      </PageHead>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="100" />
        <el-table-column prop="applyNo" label="申请单号" />
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="type" label="类型" width="120">
          <template #default="{ row }">{{ typeMap[row.type] ?? row.type }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">{{ statusMap[row.status] ?? row.status }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" />
      </el-table>
      <el-pagination
        class="pager"
        background
        layout="total, prev, pager, next"
        :total="total"
        v-model:current-page="page.current"
        :page-size="page.size"
        @current-change="onPageChange"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" title="新建采购申请" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="请输入申请标题" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" placeholder="请选择">
            <el-option v-for="(v, k) in typeMap" :key="k" :label="v" :value="Number(k)" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHead from '@/components/PageHead.vue'
import { pagePurchaseRequests, createPurchaseRequest } from '@/api/purchase'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const page = reactive({ current: 1, size: 10 })

const typeMap = { 0: '标准/项目', 1: '日常/框架', 2: '线下补录' }
const statusMap = { 0: '草稿', 1: '预算待审', 2: '采购待审', 3: '已审批', 4: '已驳回', 5: '部分转单', 6: '全部转单' }

const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({ title: '', type: 0 })

async function load() {
  loading.value = true
  try {
    const res = await pagePurchaseRequests({ current: page.current, size: page.size })
    list.value = res.data?.records || []
    // #34c：#28 后 total 为字符串，el-pagination 要求 Number
    total.value = Number(res.data?.total) || 0
  } catch (e) {
    // 后端未联通时使用空列表（契约已打通）
  } finally {
    loading.value = false
  }
}

function onPageChange(p) {
  page.current = p
  load()
}

function openDialog() {
  dialogVisible.value = true
}

async function onSubmit() {
  submitting.value = true
  try {
    await createPurchaseRequest({ ...form })
    ElMessage.success('创建成功')
    dialogVisible.value = false
    load()
  } catch (e) {
    // 错误由拦截器统一提示
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar { margin-bottom: 12px; }
.pager { margin-top: 16px; justify-content: flex-end; display: flex; }
</style>
