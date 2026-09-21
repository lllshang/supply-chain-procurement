<template>
  <div class="login">
    <van-nav-bar title="供应商登录" />
    <van-form @submit="onSubmit">
      <van-cell-group inset style="margin-top: 24px">
        <van-field v-model="username" name="username" label="账号" placeholder="请输入账号" :rules="[{ required: true }]" />
        <van-field v-model="password" type="password" name="password" label="密码" placeholder="请输入密码" :rules="[{ required: true }]" />
      </van-cell-group>
      <div style="margin: 24px 16px">
        <van-button round block type="primary" native-type="submit" :loading="loading">登 录</van-button>
      </div>
    </van-form>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showSuccessToast, showFailToast } from 'vant'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const username = ref('')
const password = ref('')
const loading = ref(false)

async function onSubmit() {
  loading.value = true
  try {
    await userStore.login(username.value, password.value)
    showSuccessToast('登录成功')
    router.push('/qual')
  } catch (e) {
    showFailToast('登录失败')
  } finally {
    loading.value = false
  }
}
</script>
