<template>
  <el-table :data="rows" stripe size="small" show-summary :summary-method="summaryMethod">
    <el-table-column prop="subjectName" label="科目" min-width="150" fixed />
    <el-table-column v-if="showProject" prop="projectName" label="项目" min-width="130" />
    <el-table-column
      v-for="p in periods"
      :key="p.value"
      :label="p.label"
      :prop="`amounts.${p.value}`"
      width="110"
      align="right"
    >
      <template #default="{ row }">{{ fmt(row.amounts[p.value]) }}</template>
    </el-table-column>
    <el-table-column label="已用" width="110" align="right">
      <template #default="{ row }">{{ fmt(row.used) }}</template>
    </el-table-column>
  </el-table>
</template>

<script setup>
// P4：审批中心来源单据跳转定位（route.query.bizId 行高亮）
import { useQueryLocate } from '@/composables/useQueryLocate'
const { rowClassName } = useQueryLocate()
import { computed } from 'vue'
import { PERIOD_OPTIONS } from '@/constants/enums'

// 12 月台账透视表：行=科目(+项目)，列=年度(period 0)+1–12 月
const props = defineProps({
  lines: { type: Array, default: () => [] },
  showProject: { type: Boolean, default: false }
})

const periods = PERIOD_OPTIONS

const rows = computed(() => {
  const map = new Map()
  for (const l of props.lines || []) {
    const key = props.showProject ? `${l.subjectId}|${l.projectId ?? ''}` : `${l.subjectId}`
    if (!map.has(key)) {
      map.set(key, {
        subjectId: l.subjectId,
        subjectName: l.subjectName,
        projectId: l.projectId,
        projectName: l.projectName,
        amounts: {},
        used: 0
      })
    }
    const row = map.get(key)
    row.amounts[l.period] = l.amount
    row.used += Number(l.usedAmount || 0)
  }
  return [...map.values()]
})

function fmt(v) {
  if (v === null || v === undefined || v === '') return '-'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function summaryMethod({ columns }) {
  return columns.map((col, index) => {
    if (index === 0) return '合计'
    if (col.property && col.property.startsWith('amounts.')) {
      const period = Number(col.property.split('.')[1])
      const total = rows.value.reduce((s, r) => s + Number(r.amounts[period] || 0), 0)
      return fmt(total)
    }
    if (col.label === '已用') {
      const total = rows.value.reduce((s, r) => s + Number(r.used || 0), 0)
      return fmt(total)
    }
    return ''
  })
}
</script>
