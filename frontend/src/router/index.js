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
      // ---- P1 商品库（catalog） ----
      { path: 'catalog/category', name: 'CatalogCategory', component: () => import('@/views/catalog/CategoryConfig.vue'), meta: { title: '品类配置' } },
      { path: 'catalog/unit', name: 'CatalogUnit', component: () => import('@/views/catalog/UnitConfig.vue'), meta: { title: '单位配置' } },
      { path: 'catalog/spec', name: 'CatalogSpec', component: () => import('@/views/catalog/SpecConfig.vue'), meta: { title: '规格配置' } },
      { path: 'catalog/price-rule', name: 'CatalogPriceRule', component: () => import('@/views/catalog/PriceRuleConfig.vue'), meta: { title: '价格规则' } },
      { path: 'catalog/product', name: 'CatalogProduct', component: () => import('@/views/catalog/ProductList.vue'), meta: { title: '产品库' } },
      { path: 'catalog/import', name: 'CatalogImport', component: () => import('@/views/catalog/ProductImport.vue'), meta: { title: '产品导入' } },
      // ---- P1 供应商（supplier） ----
      { path: 'supplier/category', name: 'SupplierCategory', component: () => import('@/views/supplier/SupplierCategory.vue'), meta: { title: '供应商分类' } },
      { path: 'supplier/list', name: 'SupplierList', component: () => import('@/views/supplier/SupplierList.vue'), meta: { title: '供应商档案' } },
      { path: 'supplier/bind', name: 'SupplierBind', component: () => import('@/views/supplier/SupplierSkuBind.vue'), meta: { title: '产品绑定' } },
      { path: 'supplier/qual', name: 'SupplierQual', component: () => import('@/views/supplier/SupplierQual.vue'), meta: { title: '资质管理' } },
      // ---- P1 预算（budget） ----
      { path: 'budget/subject', name: 'BudgetSubject', component: () => import('@/views/budget/BudgetSubject.vue'), meta: { title: '预算科目' } },
      { path: 'budget/project', name: 'BudgetProject', component: () => import('@/views/budget/BudgetProject.vue'), meta: { title: '预算项目' } },
      { path: 'budget/import', name: 'BudgetImport', component: () => import('@/views/budget/BudgetImport.vue'), meta: { title: '年度预算导入' } },
      { path: 'budget/ledger', name: 'BudgetLedger', component: () => import('@/views/budget/BudgetLedger.vue'), meta: { title: '预算台账' } },
      // ---- 其余模块占位（保持不变） ----
      { path: 'system', name: 'System', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '系统管理' } },
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
