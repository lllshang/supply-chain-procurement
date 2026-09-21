import { defineStore } from 'pinia'
import request from '@/utils/request'

export const useUserStore = defineStore('h5-user', {
  state: () => ({
    token: localStorage.getItem('h5_token') || '',
    userInfo: null
  }),
  actions: {
    async login(username, password) {
      const res = await request.post('/h5/api/v1/auth/login', { username, password })
      this.token = res.data.token
      localStorage.setItem('h5_token', this.token)
      return res
    },
    logout() {
      localStorage.removeItem('h5_token')
      this.token = ''
      this.userInfo = null
    }
  }
})
