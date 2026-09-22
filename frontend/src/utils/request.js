import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

// 统一请求封装：请求拦截注入 token，响应拦截统一处理业务码与 401
const service = axios.create({
  baseURL: '/',
  timeout: 15000
})

service.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = 'Bearer ' + token
    }
    return config
  },
  (error) => Promise.reject(error)
)

service.interceptors.response.use(
  async (response) => {
    // blob 响应（导出/模板/下载）：#30 若后端实际返回 JSON 错误体（业务码失败），
    // 解析并走统一错误提示，避免把错误 JSON 当文件保存
    if (response.config.responseType === 'blob' && response.data instanceof Blob) {
      if (response.data.type && response.data.type.includes('application/json')) {
        const text = await response.data.text()
        let err = {}
        try {
          err = JSON.parse(text)
        } catch (e) {
          err = {}
        }
        if (err && typeof err.code === 'number' && err.code !== 0) {
          ElMessage.error(err.message || '请求失败')
          return Promise.reject(new Error(err.message || 'Error'))
        }
      }
      return response.data
    }
    const res = response.data
    // 约定响应体含 { code, message, data, traceId }
    if (res && typeof res.code === 'number' && res.code !== 0) {
      ElMessage.error(res.message || '请求失败')
      // 未登录 / token 失效 → 跳转登录
      if (res.code === 2001 || res.code === 2002 || res.code === 2003) {
        localStorage.removeItem('token')
        router.push('/login')
      }
      return Promise.reject(new Error(res.message || 'Error'))
    }
    return res
  },
  (error) => {
    const msg = error.response?.data?.message || error.message || '网络异常'
    ElMessage.error(msg)
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      router.push('/login')
    }
    return Promise.reject(error)
  }
)

export default service
