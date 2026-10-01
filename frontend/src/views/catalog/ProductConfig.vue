<template>
  <el-card>
    <PageHead title="产品配置">
      <span class="hint">品类 / 单位 / 规格 / 价格规则统一在此维护（原型「产品配置」Tab 页）</span>
    </PageHead>
    <el-tabs v-model="active">
      <el-tab-pane label="品类配置" name="category">
        <CategoryConfig />
      </el-tab-pane>
      <el-tab-pane label="单位配置" name="unit">
        <UnitConfig />
      </el-tab-pane>
      <el-tab-pane label="规格配置" name="spec">
        <SpecConfig />
      </el-tab-pane>
      <el-tab-pane label="价格规则" name="price">
        <PriceRuleConfig />
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { ref } from 'vue'
import PageHead from '@/components/PageHead.vue'
import CategoryConfig from '@/views/catalog/CategoryConfig.vue'
import UnitConfig from '@/views/catalog/UnitConfig.vue'
import SpecConfig from '@/views/catalog/SpecConfig.vue'
import PriceRuleConfig from '@/views/catalog/PriceRuleConfig.vue'

// 产品配置：原型将品类/单位/规格/价格规则合并到同一 Tab 页，对齐菜单结构。
const active = ref('category')
</script>

<style scoped>
.hint {
  color: #909399;
  font-size: 12px;
}
:deep(.el-tabs__content) {
  padding-top: 8px;
}
</style>
