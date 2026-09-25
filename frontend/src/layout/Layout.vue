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
          <!-- P4 站内通知红点（设计 §3.2：顶栏消息位挂角标，下拉最近 20 条 + 全部已读） -->
          <el-badge :value="unread" :hidden="!unread" :max="99" class="notice-badge">
            <el-dropdown trigger="click" @visible-change="onNoticeDropdown">
              <span class="notice-bell" title="站内通知">
                <el-icon><Bell /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu class="notice-menu">
                  <div class="notice-head">
                    <span>站内通知</span>
                    <el-button link type="primary" size="small" @click="onReadAll">全部已读</el-button>
                  </div>
                  <template v-if="notices.length">
                    <el-dropdown-item
                      v-for="n in notices"
                      :key="n.id"
                      :class="{ 'notice-unread': !n.readFlag }"
                      @click="onNoticeClick(n)"
                    >
                      <div class="notice-item">
                        <div class="notice-title">{{ n.title }}</div>
                        <div class="notice-content">{{ n.content || '' }}</div>
                        <div class="notice-time">{{ n.createdAt }}</div>
                      </div>
                    </el-dropdown-item>
                  </template>
                  <el-dropdown-item v-else disabled>暂无通知</el-dropdown-item>
                  <el-dropdown-item divided command="goto-todo">前往待办审批</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </el-badge>
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
  fetchUnread()
  // P4 红点轮询（60s；通知失败静默，不影响主流程）
  noticeTimer = setInterval(fetchUnread, 60000)
})

onBeforeUnmount(() => {
  if (noticeTimer) clearInterval(noticeTimer)
})

// ---------------- P4 站内通知红点 ----------------
let noticeTimer = null
const unread = ref(0)
const notices = ref([])

async function fetchUnread() {
  try {
    const res = await unreadCount()
    // #28 契约：Long→String 序列化后转 Number 供 badge 展示
    unread.value = Number(res?.data) || 0
  } catch (e) {
    /* 静默 */
  }
}

async function onNoticeDropdown(visible) {
  if (!visible) return
  try {
    const res = await pageNotices({ current: 1, size: 20 })
    notices.value = res?.data?.records || []
  } catch (e) {
    /* 静默 */
  }
}

async function onReadAll() {
  try {
    await readAllNotices()
    unread.value = 0
    notices.value = notices.value.map((n) => ({ ...n, readFlag: 1 }))
  } catch (e) {
    /* 静默 */
  }
}

async function onNoticeClick(n) {
  if (!n.readFlag) {
    try {
      await markNoticeRead(n.id)
      n.readFlag = 1
      fetchUnread()
    } catch (e) {
      /* 静默 */
    }
  }
  // 点击通知跳转工作台待办（设计 §3.2）
  gotoTodo()
}

function gotoTodo() {
  router.push('/approval/todo')
}

function onCommand(cmd) {
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
  } else if (cmd === 'goto-todo') {
    gotoTodo()
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

/* ---- P4 站内通知红点 ---- */
.notice-badge {
  display: inline-flex;
  margin-right: 6px;
}
.notice-bell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  color: #fff;
  font-size: 17px;
  cursor: pointer;
}
.notice-bell:hover {
  background: rgba(255, 255, 255, 0.14);
}
.notice-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 14px;
  font-weight: 600;
  color: #303744;
}
.notice-menu {
  width: 320px;
  max-height: 420px;
  overflow: auto;
}
.notice-item {
  line-height: 1.4;
}
.notice-item .notice-title {
  font-weight: 500;
}
.notice-item .notice-content {
  color: #606266;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.notice-item .notice-time {
  color: #909399;
  font-size: 11px;
}
:deep(.notice-unread) {
  background: #f0f7ff;
}

/* ---- 内容区 ---- */
.main {
  height: calc(100% - var(--app-header-height));
  padding: 16px;
  overflow: auto;
  background: var(--app-bg-page);
}
</style>
