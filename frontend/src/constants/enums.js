// 前端本地枚举常量表（P1 前端设计 §3.4）
// 后端暂无字典接口，枚举在本文件集中维护（与既有 PurchaseRequest.vue 本地 map 约定一致）。
// 每项形如 { value, label, type? }；type 为 el-tag 语义色（成功/警告/危险/信息），可选。

export const ENUMS = {
  // 品类 / 分类 / 科目层级
  categoryLevel: [
    { value: 1, label: '一级' },
    { value: 2, label: '二级' },
    { value: 3, label: '三级' }
  ],
  // 商品状态
  productStatus: [
    { value: 0, label: '正常', type: 'success' },
    { value: 1, label: '停用', type: 'info' }
  ],
  // 供应商合作状态
  coopStatus: [
    { value: 0, label: '正常', type: 'success' },
    { value: 1, label: '停用', type: 'info' },
    { value: 2, label: '冻结', type: 'danger' }
  ],
  // 黑名单
  blacklist: [
    { value: 0, label: '否', type: 'success' },
    { value: 1, label: '是', type: 'danger' }
  ],
  // 供应商来源
  supplierSource: [
    { value: 0, label: '平台录入' },
    { value: 1, label: 'H5提交' },
    { value: 2, label: '导入' }
  ],
  // 计价方式
  valuationType: [
    { value: 0, label: '计件' },
    { value: 1, label: '计重' }
  ],
  // 绑定范围
  bindScope: [
    { value: 0, label: '不限定' },
    { value: 1, label: '限定报价接单' }
  ],
  // 价格规则类型
  priceRuleType: [
    { value: 1, label: '最低限价' },
    { value: 2, label: '最高限价' },
    { value: 3, label: '区间' },
    { value: 4, label: '公式' }
  ],
  // 价格规则引用类型
  priceRefType: [
    { value: 1, label: '商品' },
    { value: 2, label: '品类' }
  ],
  // 预算科目类型
  subjectType: [
    { value: 1, label: '支出' },
    { value: 2, label: '收入' }
  ],
  // 资质审核状态
  qualStatus: [
    { value: 0, label: '待审', type: 'warning' },
    { value: 1, label: '通过', type: 'success' },
    { value: 2, label: '驳回', type: 'danger' }
  ],
  // 导入任务状态
  importTaskStatus: [
    { value: 'RUNNING', label: '进行中', type: 'warning' },
    { value: 'SUCCESS', label: '成功', type: 'success' },
    { value: 'FAILED', label: '失败', type: 'danger' }
  ],
  // 预算头状态
  budgetHeaderStatus: [
    { value: 0, label: '草稿', type: 'info' },
    { value: 1, label: '生效', type: 'success' },
    { value: 2, label: '归档', type: 'warning' }
  ]
}

// 字符串派生枚举（非数值）
export const STRING_ENUMS = {
  // 资质有效期（后端派生：VALID / EXPIRING / EXPIRED；NONE 为无资质）
  qualValidity: [
    { value: 'VALID', label: '有效', type: 'success' },
    { value: 'EXPIRING', label: '即将到期', type: 'warning' },
    { value: 'EXPIRED', label: '已过期', type: 'danger' },
    { value: 'NONE', label: '无', type: 'info' }
  ]
}

// 预算期间：0=年度，1-12=月
export const PERIOD_OPTIONS = [
  { value: 0, label: '年度' },
  ...Array.from({ length: 12 }, (_, i) => ({ value: i + 1, label: `${i + 1}月` }))
]

function resolveOptions(enumKey) {
  if (enumKey === 'period') return PERIOD_OPTIONS
  return ENUMS[enumKey] || STRING_ENUMS[enumKey] || []
}

// 返回枚举下拉选项（浅拷贝，避免外部修改常量）
export function enumOptions(enumKey) {
  return resolveOptions(enumKey).map((o) => ({ ...o }))
}

// 值 → 中文标签（未命中回退原值）
export function enumLabel(enumKey, value) {
  const hit = resolveOptions(enumKey).find((o) => o.value === value)
  return hit ? hit.label : value
}

// 值 → el-tag 语义色（未命中回退 info）
export function enumType(enumKey, value) {
  const hit = resolveOptions(enumKey).find((o) => o.value === value)
  return hit && hit.type ? hit.type : 'info'
}

export default { ENUMS, STRING_ENUMS, PERIOD_OPTIONS, enumOptions, enumLabel, enumType }
