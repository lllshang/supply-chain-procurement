<template>
  <div class="price-rule-shell">
    <div class="price-rule-card">
      <div class="price-rule-icon">
        <el-icon><Coin /></el-icon>
      </div>
      <div class="price-rule-main">
        <div class="price-rule-heading">
          <div>
            <strong>采购最低价同步标准价</strong>
            <p>开启后，系统按有效采购订单中的最低单价更新产品标准价。</p>
          </div>
          <el-switch
            v-model="enabled"
            inline-prompt
            active-text="开启"
            inactive-text="关闭"
            :loading="saving"
            @change="onChange"
          />
        </div>
        <div class="price-rule-meta">
          <span>影响范围：全产品</span>
          <span>更新字段：标准价</span>
          <span>更新时点：采购订单生成或变更</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Coin } from '@element-plus/icons-vue'
import { getLowestOrderSync, applyLowestOrderSync } from '@/api/catalog'

const enabled = ref(false)
const saving = ref(false)

async function load() {
  try {
    const res = await getLowestOrderSync()
    const data = res && res.data
    enabled.value = !!(data && data.enabled)
  } catch (e) {
    enabled.value = false
  }
}

async function onChange(val) {
  saving.value = true
  try {
    const res = await applyLowestOrderSync(val)
    const count = (res && res.data) || 0
    if (val) {
      ElMessage.success(
        count > 0
          ? `已开启，并更新 ${count} 个产品的标准价`
          : '已开启，暂无可用于更新标准价的历史采购订单',
      )
    } else {
      ElMessage.success('已停止按采购最低价自动更新标准价')
    }
  } catch (e) {
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.price-rule-shell {
  max-width: 1180px;
  margin: 0 auto;
  padding-top: 18px;
}

.price-rule-card {
  display: flex;
  gap: 16px;
  padding: 20px;
  border: 1px solid #dbe7f5;
  border-radius: 10px;
  background: #f8fbff;
}

.price-rule-icon {
  display: grid;
  flex: 0 0 42px;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 10px;
  background: #e7f1ff;
  color: #409eff;
  font-size: 21px;
}

.price-rule-main {
  min-width: 0;
  flex: 1;
}

.price-rule-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.price-rule-heading strong {
  color: #1f2937;
  font-size: 16px;
}

.price-rule-heading p {
  max-width: 720px;
  margin: 7px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

.price-rule-heading :deep(.el-switch) {
  flex: 0 0 auto;
  --el-switch-on-color: #409eff;
}

.price-rule-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #e2e8f0;
  color: #475569;
  font-size: 12px;
}
</style>
