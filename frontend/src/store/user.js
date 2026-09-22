import { defineStore } from 'pinia'
import request from '@/utils/request'

// 后端不可用时的本地兜底菜单（与后端 /api/v1/menu 契约一致）
// P1：为商品库/供应商/预算三组补充二级 children（parentId 指向父项 id），叶子带 perms。
// perms 同时含 read/write 键，与 backend db/data.sql 的 sys_menu 种子保持一致，
// 供 v-permission 判定（缺 write 键会导致写按钮被隐藏）。
export const fallbackMenus = [
  { id: 1, parentId: 0, menuName: '系统管理', icon: 'setting', path: '/system', menuType: 1 },
  {
    id: 2,
    parentId: 0,
    menuName: '商品库管理',
    icon: 'box',
    path: '/catalog',
    menuType: 1,
    children: [
      { id: 201, parentId: 2, menuName: '品类配置', icon: 'list', path: '/catalog/category', menuType: 2, perms: 'catalog:category:read,catalog:category:write' },
      { id: 202, parentId: 2, menuName: '单位配置', icon: 'scale-to-original', path: '/catalog/unit', menuType: 2, perms: 'catalog:unit:read,catalog:unit:write' },
      { id: 203, parentId: 2, menuName: '规格配置', icon: 'operation', path: '/catalog/spec', menuType: 2, perms: 'catalog:spec:read,catalog:spec:write' },
      { id: 204, parentId: 2, menuName: '价格规则', icon: 'price-tag', path: '/catalog/price-rule', menuType: 2, perms: 'catalog:price:read,catalog:price:write' },
      { id: 205, parentId: 2, menuName: '产品库', icon: 'goods', path: '/catalog/product', menuType: 2, perms: 'catalog:spu:read,catalog:spu:write' },
      { id: 206, parentId: 2, menuName: '产品导入', icon: 'upload', path: '/catalog/import', menuType: 2, perms: 'catalog:import' }
    ]
  },
  {
    id: 3,
    parentId: 0,
    menuName: '供应商管理',
    icon: 'shop',
    path: '/supplier',
    menuType: 1,
    children: [
      { id: 301, parentId: 3, menuName: '供应商分类', icon: 'share', path: '/supplier/category', menuType: 2, perms: 'supplier:read,supplier:write' },
      { id: 302, parentId: 3, menuName: '供应商档案', icon: 'shop', path: '/supplier/list', menuType: 2, perms: 'supplier:read,supplier:write' },
      { id: 303, parentId: 3, menuName: '产品绑定', icon: 'link', path: '/supplier/bind', menuType: 2, perms: 'supplier:read,supplier:write' },
      { id: 304, parentId: 3, menuName: '资质管理', icon: 'medal', path: '/supplier/qual', menuType: 2, perms: 'supplier:read,supplier:write' }
    ]
  },
  {
    id: 4,
    parentId: 0,
    menuName: '采购管理',
    icon: 'shopping',
    path: '/purchase',
    menuType: 1,
    children: [
      { id: 401, parentId: 4, menuName: '采购申请', icon: 'list', path: '/purchase/apply', menuType: 2, perms: 'purchase:apply:read,purchase:apply:write,purchase:apply:export' },
      { id: 402, parentId: 4, menuName: '常购清单', icon: 'goods', path: '/purchase/frequent', menuType: 2, perms: 'purchase:frequent:read,purchase:frequent:write' },
      { id: 403, parentId: 4, menuName: '询价管理', icon: 'trend', path: '/purchase/inquiry', menuType: 2, perms: 'purchase:inquiry:read,purchase:inquiry:write' },
      { id: 404, parentId: 4, menuName: '报价管理', icon: 'upload', path: '/purchase/quotation', menuType: 2, perms: 'purchase:quotation:read,purchase:quotation:write' },
      { id: 405, parentId: 4, menuName: '比价定标', icon: 'medal', path: '/purchase/award', menuType: 2, perms: 'purchase:award:read,purchase:award:write,purchase:award:submit' }
    ]
  },
  {
    id: 5,
    parentId: 0,
    menuName: '预算管理',
    icon: 'money',
    path: '/budget',
    menuType: 1,
    children: [
      { id: 501, parentId: 5, menuName: '预算科目', icon: 'notebook', path: '/budget/subject', menuType: 2, perms: 'budget:subject:read,budget:subject:write' },
      { id: 502, parentId: 5, menuName: '预算项目', icon: 'folder', path: '/budget/project', menuType: 2, perms: 'budget:project:read,budget:project:write' },
      { id: 503, parentId: 5, menuName: '年度预算导入', icon: 'upload', path: '/budget/import', menuType: 2, perms: 'budget:import' },
      { id: 504, parentId: 5, menuName: '预算台账', icon: 'money', path: '/budget/ledger', menuType: 2, perms: 'budget:read' }
    ]
  },
  { id: 6, parentId: 0, menuName: '合同管理', icon: 'document', path: '/contract', menuType: 1 },
  { id: 7, parentId: 0, menuName: '报价定标', icon: 'trend', path: '/quotation', menuType: 1 },
  {
    id: 8,
    parentId: 0,
    menuName: '订单与验收',
    icon: 'list',
    path: '/order',
    menuType: 1,
    children: [
      { id: 801, parentId: 8, menuName: '采购订单', icon: 'list', path: '/order/list', menuType: 2, perms: 'order:read,order:write,order:change' },
      { id: 802, parentId: 8, menuName: '到货验收', icon: 'box', path: '/order/arrival', menuType: 2, perms: 'arrival:read,arrival:write,arrival:confirm' },
      { id: 803, parentId: 8, menuName: '入库台账', icon: 'money', path: '/order/ledger', menuType: 2, perms: 'arrival:read' },
      { id: 804, parentId: 8, menuName: '履约调整', icon: 'tools', path: '/order/adjust', menuType: 2, perms: 'adjust:read,adjust:write' },
      { id: 805, parentId: 8, menuName: '服务考核', icon: 'audit', path: '/order/assess', menuType: 2, perms: 'order:assess:read,order:assess:write' }
    ]
  },
  { id: 9, parentId: 0, menuName: '结算与付款', icon: 'credit-card', path: '/settlement', menuType: 1 },
  { id: 10, parentId: 0, menuName: '审批中心', icon: 'audit', path: '/approval', menuType: 1 },
  { id: 11, parentId: 0, menuName: '报表中心', icon: 'chart', path: '/report', menuType: 1 },
  { id: 12, parentId: 0, menuName: '基础设置', icon: 'tools', path: '/setting', menuType: 1 }
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
