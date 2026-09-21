import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layout/Layout.vue'),
    redirect: '/purchase',
    children: [
      { path: 'purchase', name: 'PurchaseRequest', component: () => import('@/views/PurchaseRequest.vue'), meta: { title: '采购申请' } },
      { path: 'system', name: 'System', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '系统管理' } },
      { path: 'catalog', name: 'Catalog', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '商品库管理' } },
      { path: 'supplier', name: 'Supplier', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '供应商管理' } },
      { path: 'budget', name: 'Budget', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '预算管理' } },
      { path: 'contract', name: 'Contract', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '合同管理' } },
      { path: 'quotation', name: 'Quotation', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '报价定标' } },
      { path: 'order', name: 'Order', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '订单与验收' } },
      { path: 'settlement', name: 'Settlement', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '结算与付款' } },
      { path: 'approval', name: 'Approval', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '审批中心' } },
      { path: 'report', name: 'Report', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '报表中心' } },
      { path: 'setting', name: 'Setting', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '基础设置' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (!token && !to.meta.public) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/')
  } else {
    next()
  }
})

export default router
