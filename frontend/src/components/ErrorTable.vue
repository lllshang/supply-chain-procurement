<template>
  <el-table
    v-if="errors && errors.length"
    :data="errors"
    size="small"
    border
    max-height="240"
    @row-click="onRowClick"
  >
    <el-table-column prop="row" label="行" width="80" />
    <el-table-column prop="col" label="列" width="140" />
    <el-table-column prop="msg" label="错误信息" show-overflow-tooltip />
  </el-table>
  <el-empty v-else description="无错误" :image-size="60" />
</template>

<script setup>
// 导入错误定位表：渲染 ImportErrorVO[]{row,col,msg}，点击行回调
defineProps({
  errors: { type: Array, default: () => [] }
})
const emit = defineEmits(['row-click'])

function onRowClick(row) {
  emit('row-click', row)
}
</script>
