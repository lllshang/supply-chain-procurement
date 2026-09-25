// P4 审批中心单点配置（docs/P4审批中心设计.md §4.3）
// 8 bizType → 来源单据列表页跳转映射；bizType 增删只改本文件（随 ApprovalBizTypes 维护）。

// 业务类型页签（8 类，对齐 PRD §6.16 七类 + DAILY_AUTH）
export const APPROVAL_BIZ_TYPES = [
  { value: 'PURCHASE_APPLY', label: '采购申请' },
  { value: 'AWARD', label: '定标' },
  { value: 'CONTRACT', label: '合同' },
  { value: 'FULFILLMENT_ADJUST', label: '履约调整' },
  { value: 'BUDGET', label: '预算升级' },
  { value: 'SETTLEMENT', label: '结算' },
  { value: 'SUPPLIER_QUAL', label: '供应商资质' },
  { value: 'DAILY_AUTH', label: '日常超授权' }
]

export function bizTypeLabel(value) {
  const hit = APPROVAL_BIZ_TYPES.find((t) => t.value === value)
  return hit ? hit.label : value
}

// 来源单据跳转映射（列表页 + query 定位高亮，最小改动方案）
export const BIZ_TYPE_ROUTES = {
  PURCHASE_APPLY: { path: '/purchase/apply', queryKey: 'bizId' },
  AWARD: { path: '/purchase/award', queryKey: 'bizId' },
  CONTRACT: { path: '/contract/list', queryKey: 'bizId' },
  FULFILLMENT_ADJUST: { path: '/order/adjust', queryKey: 'bizId' },
  BUDGET: { path: '/budget/ledger', queryKey: 'bizId' },
  SETTLEMENT: { path: '/settlement/list', queryKey: 'bizId' },
  SUPPLIER_QUAL: { path: '/supplier/qual', queryKey: 'bizId' },
  DAILY_AUTH: { path: '/order/list', queryKey: 'bizId' }
}

/** 构造来源单据跳转地址（bizId 字符串直传，零 Number() 转换）。 */
export function bizTypeRoute(bizType, bizId) {
  const target = BIZ_TYPE_ROUTES[bizType]
  if (!target || bizId === null || bizId === undefined) {
    return null
  }
  return { path: target.path, query: { [target.queryKey]: String(bizId) } }
}

// 节点状态（approval_node.status int 直传）
export const NODE_STATUS = [
  { value: 0, label: '待审', type: 'warning' },
  { value: 1, label: '已审结', type: 'success' },
  { value: 2, label: '已跳过', type: 'info' }
]

export function nodeStatusLabel(value) {
  const hit = NODE_STATUS.find((s) => s.value === value)
  return hit ? hit.label : value
}

// 审批动作（approval_record.action）
export function actionLabel(value) {
  if (value === 'APPROVE') return '同意'
  if (value === 'REJECT') return '驳回'
  return value
}
