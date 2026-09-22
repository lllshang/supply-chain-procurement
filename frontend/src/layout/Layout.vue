<template>
  <el-container class="layout">
    <el-aside :width="'224px'" class="aside">
      <div class="brand">
        <span class="brand-mark">供</span>
        <span class="brand-text">供应链中台</span>
      </div>
      <el-menu class="side-menu" :default-active="activeMenu" router>
        <template v-for="m in menus" :key="m.id">
          <el-sub-menu v-if="m.children && m.children.length" :index="m.path || String(m.id)">
            <template #title>
              <span>{{ m.menuName }}</span>
            </template>
            <el-menu-item v-for="c in m.children" :key="c.id" :index="c.path">
              <span>{{ c.menuName }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="m.path">
            <span>{{ m.menuName }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container class="layout-body">
      <el-header class="header" height="60px">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
        </el-breadcrumb>
        <el-dropdown @command="onCommand">
          <span class="user-trigger">
            {{ userStore.userInfo?.username || '管理员' }}<el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <!-- 页头标题（对齐原型：左侧 3px 主色竖条 + 22px 标题） -->
        <div class="page-head">
          <h1 class="page-title">{{ currentTitle }}</h1>
        </div>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const menus = ref([])

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta?.title || '')

onMounted(async () => {
  menus.value = await userStore.fetchMenus()
  // 刷新后 Pinia 状态丢失，需重新拉取用户信息以填充 perms（供 v-permission 使用）
  if (!userStore.userInfo) {
    try {
      await userStore.fetchUserInfo()
    } catch (e) {
      // 后端不可用时忽略，按钮级权限将按“放行”兜底
    }
  }
})

function onCommand(cmd) {
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
/* ==========================================================================
   布局骨架（对齐原型：白色侧边栏 + 蓝色渐变顶栏 + 浅灰蓝内容区）
   仅样式调整，逻辑未动。
   ========================================================================== */
.layout { height: 100vh; overflow: hidden; }

/* ---- 侧边栏 ---- */
.aside {
  display: flex;
  flex-direction: column;
  background: #fff;
  border-right: 1px solid var(--app-border-aside);
  overflow: hidden;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: var(--app-header-height);
  flex: none;
  padding: 0 18px;
  overflow: hidden;
  white-space: nowrap;
  color: #fff;
  background: var(--app-gradient-brand);
}
.brand-mark {
  display: grid;
  place-items: center;
  flex: none;
  width: 30px;
  height: 30px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.18);
  font-weight: 700;
}
.brand-text {
  font-size: 15px;
  font-weight: 600;
}

/* ---- 菜单 ---- */
.side-menu {
  --el-menu-bg-color: #fff;
  --el-menu-hover-bg-color: var(--app-bg-menu-hover);
  --el-menu-active-color: var(--app-color-primary);
  --el-menu-text-color: #303744;
  flex: 1;
  width: var(--app-sidebar-width);
  height: calc(100% - var(--app-header-height));
  overflow: hidden auto;
  border-right: 0;
}
.side-menu :deep(.el-menu-item),
.side-menu :deep(.el-sub-menu__title) {
  height: 46px;
  font-size: 14px;
}
.side-menu :deep(.el-menu-item:hover),
.side-menu :deep(.el-sub-menu__title:hover) {
  color: var(--app-color-primary-hover);
  background: var(--app-bg-menu-hover);
}
.side-menu :deep(.el-menu-item.is-active) {
  color: #fff;
  background: var(--app-color-primary);
}
.side-menu :deep(.el-menu--inline) {
  background: #fff;
}

/* ---- 顶栏 ---- */
.layout-body { min-width: 0; height: 100%; }
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  color: #fff;
  background: var(--app-gradient-header);
  box-shadow: 0 2px 8px rgba(36, 116, 201, 0.18);
}
.header :deep(.el-breadcrumb__inner),
.header :deep(.el-breadcrumb__separator) {
  color: rgba(255, 255, 255, 0.9);
}
.header :deep(.el-breadcrumb__inner.is-link:hover) {
  color: #fff;
}
.user-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  padding: 0 10px;
  border-radius: 6px;
  color: #fff;
  cursor: pointer;
}
.user-trigger:hover {
  background: rgba(255, 255, 255, 0.14);
}

/* ---- 内容区 ---- */
.main {
  height: calc(100% - var(--app-header-height));
  padding: 16px;
  overflow: auto;
  background: var(--app-bg-page);
}
.page-head {
  margin-bottom: 16px;
}
.page-title {
  position: relative;
  margin: 0;
  padding-left: 12px;
  color: var(--app-text-primary);
  font-size: 22px;
  font-weight: 600;
  line-height: 1.3;
}
.page-title::before {
  content: "";
  position: absolute;
  top: 3px;
  bottom: 3px;
  left: 0;
  width: 3px;
  border-radius: 2px;
  background: var(--app-color-primary);
}
</style>
