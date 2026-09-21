<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">供应链中台</div>
      <el-menu :default-active="activeMenu" background-color="#001529" text-color="#fff" active-text-color="#409EFF" router>
        <el-menu-item v-for="m in menus" :key="m.id" :index="m.path">
          <span>{{ m.menuName }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
        </el-breadcrumb>
        <el-dropdown @command="onCommand">
          <span class="user">{{ userStore.userInfo?.username || '管理员' }}<el-icon><ArrowDown /></el-icon></span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main>
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
})

function onCommand(cmd) {
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout { height: 100vh; }
.aside { background: #001529; }
.logo { color: #fff; text-align: center; line-height: 60px; font-weight: bold; }
.header { display: flex; align-items: center; justify-content: space-between; background: #fff; border-bottom: 1px solid #eee; }
.user { cursor: pointer; display: flex; align-items: center; gap: 4px; }
</style>
