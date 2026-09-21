import { defineStore } from 'pinia'
import request from '@/utils/request'

// 后端不可用时的本地兜底菜单（与后端 /api/v1/menu 契约一致）
export const fallbackMenus = [
  { id: 1, parentId: 0, menuName: '系统管理', path: '/system', menuType: 1 },
  { id: 2, parentId: 0, menuName: '商品库管理', path: '/catalog', menuType: 1 },
  { id: 3, parentId: 0, menuName: '供应商管理', path: '/supplier', menuType: 1 },
  { id: 4, parentId: 0, menuName: '采购管理', path: '/purchase', menuType: 1 },
  { id: 5, parentId: 0, menuName: '预算管理', path: '/budget', menuType: 1 },
  { id: 6, parentId: 0, menuName: '合同管理', path: '/contract', menuType: 1 },
  { id: 7, parentId: 0, menuName: '报价定标', path: '/quotation', menuType: 1 },
  { id: 8, parentId: 0, menuName: '订单与验收', path: '/order', menuType: 1 },
  { id: 9, parentId: 0, menuName: '结算与付款', path: '/settlement', menuType: 1 },
  { id: 10, parentId: 0, menuName: '审批中心', path: '/approval', menuType: 1 },
  { id: 11, parentId: 0, menuName: '报表中心', path: '/report', menuType: 1 },
  { id: 12, parentId: 0, menuName: '基础设置', path: '/setting', menuType: 1 }
]

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: null,
    menus: []
  }),
  actions: {
    async login(username, password) {
      const res = await request.post('/api/v1/auth/login', { username, password })
      this.token = res.data.token
      localStorage.setItem('token', this.token)
      return res
    },
    async fetchUserInfo() {
      const res = await request.get('/api/v1/auth/me')
      this.userInfo = res.data
      return res.data
    },
    async fetchMenus() {
      try {
        const res = await request.get('/api/v1/menu')
        this.menus = res.data && res.data.length ? res.data : fallbackMenus
      } catch (e) {
        this.menus = fallbackMenus
      }
      return this.menus
    },
    logout() {
      localStorage.removeItem('token')
      this.token = ''
      this.userInfo = null
      this.menus = []
    }
  }
})
