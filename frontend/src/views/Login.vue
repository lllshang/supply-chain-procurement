<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand-mark">供</div>
      <h1 class="login-title">供应链采购协同中台</h1>
      <p class="login-sub">后台管理端</p>
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top" @keyup.enter="onSubmit">
        <el-form-item prop="username" label="账号">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password" label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" :prefix-icon="Lock" />
        </el-form-item>
        <el-button type="primary" :loading="loading" class="login-button" @click="onSubmit">登 录</el-button>
      </el-form>
      <p class="login-tip">默认账号：admin / admin123</p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'admin123' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function onSubmit() {
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await userStore.login(form.username, form.password)
      await userStore.fetchUserInfo()
      await userStore.fetchMenus()
      ElMessage.success('登录成功')
      router.push('/')
    } catch (e) {
      // 错误信息由响应拦截器统一提示
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
/* 对齐原型登录页：浅灰蓝底 + 白卡片（380px / 圆角 8 / 柔和投影） */
.login-page {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: 24px;
  background: #f2f5f9;
}
.login-card {
  width: 380px;
  padding: 34px 32px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  background: #fff;
  box-shadow: 0 18px 50px rgba(31, 42, 61, 0.12);
}
.brand-mark {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  margin-bottom: 18px;
  border-radius: 8px;
  color: #fff;
  background: var(--app-color-primary);
  font-size: 20px;
  font-weight: 700;
}
.login-title {
  margin: 0;
  color: var(--app-text-primary);
  font-size: 22px;
  font-weight: 600;
  line-height: 1.3;
}
.login-sub {
  margin: 6px 0 22px;
  color: var(--app-text-secondary);
  font-size: 13px;
}
.login-button {
  width: 100%;
  margin-top: 6px;
}
.login-tip {
  margin: 18px 0 0;
  color: #9ca3af;
  font-size: 12px;
  text-align: center;
}
</style>
