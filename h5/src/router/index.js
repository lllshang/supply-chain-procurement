import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue') },
  { path: '/qual', name: 'QualSubmit', component: () => import('@/views/QualSubmit.vue') },
  { path: '/', redirect: '/qual' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('h5_token')
  if (!token && to.path !== '/login') next('/login')
  else next()
})

export default router
