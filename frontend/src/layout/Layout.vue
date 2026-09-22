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
              <el-icon class="menu-icon"><component :is="menuIcon(m.icon)" /></el-icon>
              <span>{{ m.menuName }}</span>
            </template>
            <el-menu-item v-for="c in m.children" :key="c.id" :index="c.path">
              <el-icon class="menu-icon"><component :is="menuIcon(c.icon)" /></el-icon>
              <span>{{ c.menuName }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="m.path">
            <el-icon class="menu-icon"><component :is="menuIcon(m.icon)" /></el-icon>
            <span>{{ m.menuName }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container class="layout-body">
      <el-header class="header" height="60px">
        <div class="header-left">
          <!-- 样式占位，功能未实现：折叠按钮仅还原原型观感，不触发任何动作 -->
          <span class="collapse-button" aria-hidden="true"><el-icon><Fold /></el-icon></span>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <!-- 样式占位，功能未实现：胶囊按钮仅还原原型观感，不触发任何动作 -->
          <el-button class="header-pill" size="small" round>演示数据</el-button>
          <el-button class="header-pill" size="small" round>供应商H5示例</el-button>
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
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowDown,
  Fold,
  Setting,
  Box,
  Shop,
  ShoppingCart,
  Money,
  Document,
  TrendCharts,
  List,
  CreditCard,
  DocumentChecked,
  DataAnalysis,
  Tools,
  Menu as MenuIcon,
  Goods,
  Upload,
  Link,
  Medal,
  Notebook,
  Folder,
  PriceTag,
  Share,
  ScaleToOriginal,
  Operation
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

// sys_menu.icon -> Element Plus 图标映射（对齐原型菜单图标观感）。
// 键与 backend db/data.sql 的 icon 值一致；映射不到时使用默认图标。
const ICON_MAP = {
  setting: Setting,
  box: Box,
  shop: Shop,
  shopping: ShoppingCart,
  money: Money,
  document: Document,
  trend: TrendCharts,
  list: List,
  'credit-card': CreditCard,
  audit: DocumentChecked,
  chart: DataAnalysis,
  tools: Tools,
  // P1 二级菜单
  goods: Goods,
  upload: Upload,
  link: Link,
  medal: Medal,
  notebook: Notebook,
  folder: Folder,
  'price-tag': PriceTag,
  share: Share,
  'scale-to-original': ScaleToOriginal,
  operation: Operation
}
const DEFAULT_ICON = MenuIcon

/** 菜单图标解析：未知 icon 回落默认图标，保证每项都有图标。 */
function menuIcon(name) {
  return (name && ICON_MAP[name]) || DEFAULT_ICON
}

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
.side-menu :deep(.el-menu-item .menu-icon),
.side-menu :deep(.el-sub-menu__title .menu-icon) {
  margin-right: 6px;
  width: 18px;
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
.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}
.collapse-button {
  display: inline-flex;
  color: #fff;
  font-size: 18px;
  cursor: default;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.header-pill {
  border: none;
  color: #fff;
  background: rgba(255, 255, 255, 0.14);
}
.header-pill:hover,
.header-pill:focus {
  border: none;
  color: #fff;
  background: rgba(255, 255, 255, 0.24);
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
</style>
