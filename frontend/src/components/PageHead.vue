<template>
  <div class="page-head">
    <h1 class="page-title">{{ title }}</h1>
    <div v-if="$slots.default" class="page-actions">
      <slot />
    </div>
  </div>
</template>

<script setup>
// 纯展示组件：卡片内页头（标题 + 右侧操作区），对齐原型 page-header 观感。
// 不承载任何业务逻辑；操作按钮由各页面以插槽传入（事件绑定留在页面内）。
defineProps({
  title: { type: String, default: '' }
})
</script>
