<template>
  <el-card>
    <el-empty :description="`「${route.meta?.title || '该'}」模块建设中（阶段二业务落地）`" />
  </el-card>
</template>

<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
</script>
