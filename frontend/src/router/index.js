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
      // ---- P3 价格库审核（事后成交价沉淀的人工复核，views/cost 域） ----
      { path: 'catalog/price-audit', name: 'CatalogPriceAudit', component: () => import('@/views/cost/PriceAuditIndex.vue'), meta: { title: '价格库审核' } },
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
      // ---- P2 采购主链路（对齐 data.sql 二级菜单路由） ----
      { path: 'purchase/apply', name: 'PurchaseApply', component: () => import('@/views/purchase/ApplyIndex.vue'), meta: { title: '采购申请' } },
      { path: 'purchase/frequent', name: 'PurchaseFrequent', component: () => import('@/views/purchase/FrequentIndex.vue'), meta: { title: '常购清单' } },
      { path: 'purchase/inquiry', name: 'PurchaseInquiry', component: () => import('@/views/purchase/InquiryIndex.vue'), meta: { title: '询价管理' } },
      { path: 'purchase/quotation', name: 'PurchaseQuotation', component: () => import('@/views/purchase/QuotationIndex.vue'), meta: { title: '报价管理' } },
      { path: 'purchase/award', name: 'PurchaseAward', component: () => import('@/views/purchase/AwardIndex.vue'), meta: { title: '比价定标' } },
      { path: 'contract/list', name: 'ContractList', component: () => import('@/views/contract/ContractIndex.vue'), meta: { title: '合同台账' } },
      { path: 'contract/types', name: 'ContractTypes', component: () => import('@/views/contract/ContractTypeConfig.vue'), meta: { title: '合同类型配置' } },
      { path: 'contract/types', name: 'ContractTypes', component: () => import('@/views/contract/ContractTypeConfig.vue'), meta: { title: '合同类型配置' } },
      { path: 'contract/warn', name: 'ContractWarn', component: () => import('@/views/contract/ContractWarn.vue'), meta: { title: '到期预警' } },
      { path: 'order/list', name: 'OrderList', component: () => import('@/views/order/OrderIndex.vue'), meta: { title: '采购订单' } },
      { path: 'order/arrival', name: 'OrderArrival', component: () => import('@/views/order/ArrivalIndex.vue'), meta: { title: '到货验收' } },
      { path: 'order/ledger', name: 'OrderLedger', component: () => import('@/views/order/LedgerIndex.vue'), meta: { title: '入库台账' } },
      { path: 'order/adjust', name: 'OrderAdjust', component: () => import('@/views/order/AdjustIndex.vue'), meta: { title: '履约调整' } },
      { path: 'order/assess', name: 'OrderAssess', component: () => import('@/views/order/AssessIndex.vue'), meta: { title: '服务考核' } },
      // ---- P4 审批中心工作台（替换占位；/approval 现网路径保留，Q15） ----
      { path: 'approval', redirect: '/approval/todo' },
      { path: 'approval/todo', name: 'ApprovalTodo', component: () => import('@/views/approval/TodoList.vue'), meta: { title: '待办审批' } },
      { path: 'approval/done', name: 'ApprovalDone', component: () => import('@/views/approval/DoneList.vue'), meta: { title: '已办审批' } },
      { path: 'approval/config', name: 'ApprovalConfig', component: () => import('@/views/approval/FlowConfig.vue'), meta: { title: '流程配置' } },
      // ---- 其余模块占位（保持不变） ----
      { path: 'system', name: 'System', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '系统管理' } },
      { path: 'contract', name: 'Contract', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '合同管理' } },
      { path: 'quotation', name: 'Quotation', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '报价定标' } },
      { path: 'order', name: 'Order', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '订单与验收' } },
      { path: 'settlement', name: 'Settlement', redirect: '/settlement/list' },
      { path: 'settlement/list', name: 'SettlementList', component: () => import('@/views/settlement/SettlementIndex.vue'), meta: { title: '结算管理' } },
      { path: 'settlement/payment', name: 'SettlementPayment', component: () => import('@/views/settlement/PaymentIndex.vue'), meta: { title: '付款登记' } },
      { path: 'settlement/statement', name: 'Statement', component: () => import('@/views/settlement/StatementIndex.vue'), meta: { title: '对账单' } },
      { path: 'report', name: 'Report', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '报表中心' } },
      { path: 'setting', name: 'Setting', component: () => import('@/views/ModulePlaceholder.vue'), meta: { title: '基础设置' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to, from, next) => {
  const token = localStorage.getItem('token')
  if (!token && !to.meta.public) {
    next('/login')
    return
  }
  if (to.path === '/login' && token) {
    next('/')
    return
  }
  // 【修复 #11】进入受保护路由前确保用户信息（含 perms）已就绪：
  // 否则整页刷新时视图先挂载、perms 后到达，导致 v-permission 判定不确定。
  if (token && !to.meta.public) {
    const userStore = useUserStore()
    if (!userStore.userInfo) {
      try {
        await userStore.fetchUserInfo()
      } catch (e) {
        // 后端不可用时忽略；按钮权限按“放行”兜底（真实拦截在后端）
      }
    }
  }
  next()
})

export default router
