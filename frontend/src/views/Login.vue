<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2 class="title">供应链采购协同中台</h2>
      <el-form :model="form" :rules="rules" ref="formRef" @keyup.enter="onSubmit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="密码" :prefix-icon="Lock" />
        </el-form-item>
        <el-button type="primary" :loading="loading" class="w100" @click="onSubmit">登 录</el-button>
      </el-form>
      <p class="tip">默认账号：admin / admin123</p>
    </el-card>
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
.login-wrap { height: 100vh; display: flex; align-items: center; justify-content: center; background: #f0f2f5; }
.login-card { width: 360px; padding: 10px 8px; }
.title { text-align: center; margin-bottom: 20px; }
.w100 { width: 100%; }
.tip { text-align: center; color: #999; font-size: 12px; margin-top: 10px; }
</style>
