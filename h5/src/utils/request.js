import axios from 'axios'
import { showToast } from 'vant'

const service = axios.create({ baseURL: '/', timeout: 15000 })

service.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('h5_token')
    if (token) config.headers['Authorization'] = 'Bearer ' + token
    return config
  },
  (error) => Promise.reject(error)
)

service.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && typeof res.code === 'number' && res.code !== 0) {
      showToast(res.message || '请求失败')
      if ([2001, 2002, 2003].includes(res.code)) {
        localStorage.removeItem('h5_token')
      }
      return Promise.reject(new Error(res.message || 'Error'))
    }
    return res
  },
  (error) => {
    showToast(error.response?.data?.message || '网络异常')
    return Promise.reject(error)
  }
)

export default service
