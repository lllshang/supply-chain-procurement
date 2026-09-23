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
  ],
  // 合同类型（ContractSaveReqVO.contractType 为 Integer，须提交数值 #33①）
  contractType: [
    { value: 0, label: '物料' },
    { value: 1, label: '服务' }
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
  ],
  // ---- P2 采购主链路（后端 IEnum 以枚举名序列化，故为字符串枚举） ----
  // 采购申请状态机
  applyStatus: [
    { value: 'DRAFT', label: '草稿', type: 'info' },
    { value: 'BUDGET_PENDING', label: '预算待审', type: 'warning' },
    { value: 'PURCHASE_PENDING', label: '采购待审', type: 'warning' },
    { value: 'APPROVED', label: '已审批', type: 'success' },
    { value: 'REJECTED', label: '已驳回', type: 'danger' },
    { value: 'PARTIAL_ORDER', label: '部分转单', type: 'warning' },
    { value: 'FULL_ORDER', label: '全部转单', type: 'success' }
  ],
  // 申请类型
  applyType: [
    { value: 'STANDARD', label: '标准/项目采购' },
    { value: 'DAILY', label: '日常/框架采购' },
    { value: 'OFFLINE', label: '线下补录' }
  ],
  // 审批任务状态
  approvalStatus: [
    { value: 'CREATED', label: '待审批', type: 'warning' },
    { value: 'IN_PROGRESS', label: '审批中', type: 'warning' },
    { value: 'APPROVED', label: '通过', type: 'success' },
    { value: 'REJECTED', label: '驳回', type: 'danger' },
    { value: 'CALLBACK_DONE', label: '已完成', type: 'success' }
  ],
  // 审批业务类型
  approvalBizType: [
    { value: 'PURCHASE_APPLY', label: '采购申请' },
    { value: 'AWARD', label: '定标' },
    { value: 'CONTRACT', label: '合同' },
    { value: 'FULFILLMENT_ADJUST', label: '履约调整' }
  ],
  // 询价状态
  inquiryStatus: [
    { value: 'DRAFT', label: '草稿', type: 'info' },
    { value: 'PUBLISHED', label: '已发布', type: 'success' },
    { value: 'CLOSED', label: '已截标', type: 'warning' },
    { value: 'CANCELLED', label: '已取消', type: 'danger' }
  ],
  // 报价状态
  quotationStatus: [
    { value: 'SUBMITTED', label: '已提交', type: 'info' },
    { value: 'ACCEPTED', label: '已采纳', type: 'success' },
    { value: 'REJECTED', label: '已否决', type: 'danger' }
  ],
  // 定标状态
  awardStatus: [
    { value: 'PENDING_APPROVAL', label: '待审批', type: 'warning' },
    { value: 'APPROVED', label: '已审批', type: 'success' },
    { value: 'REJECTED', label: '已驳回', type: 'danger' }
  ],
  // 合同状态
  contractStatus: [
    { value: 'DRAFT', label: '草稿', type: 'info' },
    { value: 'PENDING_APPROVAL', label: '待审批', type: 'warning' },
    { value: 'EFFECTIVE', label: '生效中', type: 'success' },
    { value: 'EXPIRED', label: '已过期', type: 'danger' },
    { value: 'TERMINATED', label: '已终止', type: 'danger' }
  ],
  // 订单状态
  orderStatus: [
    { value: 'CREATED', label: '已创建', type: 'warning' },
    { value: 'PARTIAL_RECEIVED', label: '部分到货', type: 'warning' },
    { value: 'RECEIVED', label: '已到货', type: 'success' },
    { value: 'SETTLED', label: '已结算', type: 'success' },
    { value: 'CANCELLED', label: '已取消', type: 'danger' },
    { value: 'PAID', label: '已付款', type: 'success' }
  ],
  // 到货验收状态
  arrivalStatus: [
    { value: 'ARRIVAL_CONFIRMED', label: '到货确认', type: 'warning' },
    { value: 'PARTIAL_STORED', label: '部分入库', type: 'warning' },
    { value: 'STORED', label: '已入库', type: 'success' }
  ],
  // 差异处理方式
  handleType: [
    { value: 'ACCEPT', label: '接受' },
    { value: 'RETURN', label: '退货' },
    { value: 'REPLENISH', label: '补货' }
  ],
  // 履约调整状态
  adjustStatus: [
    { value: 'DRAFT', label: '草稿', type: 'info' },
    { value: 'IN_APPROVAL', label: '审批中', type: 'warning' },
    { value: 'EFFECTIVE', label: '生效', type: 'success' },
    { value: 'REJECTED', label: '驳回', type: 'danger' }
  ],
  // 履约调整类型
  adjustType: [
    { value: 'DIFF', label: '差异' },
    { value: 'RETURN', label: '退货' },
    { value: 'REPLENISH', label: '补货' },
    { value: 'CHANGE', label: '变更' }
  ],
  // 履约调整业务类型
  adjustBizType: [
    { value: 'ORDER', label: '订单' },
    { value: 'ARRIVAL', label: '到货' }
  ],
  // 供应商合作状态（#33 name 契约：详情/分页均吐 name）
  coopStatus: [
    { value: 'NORMAL', label: '正常', type: 'success' },
    { value: 'DISABLED', label: '停用', type: 'info' },
    { value: 'FROZEN', label: '冻结', type: 'danger' }
  ],
  // 黑名单
  blacklist: [
    { value: 'NO', label: '否', type: 'success' },
    { value: 'YES', label: '是', type: 'danger' }
  ],
  // 供应商来源
  supplierSource: [
    { value: 'PLATFORM', label: '平台录入' },
    { value: 'H5', label: 'H5提交' },
    { value: 'IMPORT', label: '导入' }
  ],
  // 绑定范围
  bindScope: [
    { value: 'UNLIMITED', label: '不限定' },
    { value: 'LIMITED', label: '限定报价接单' }
  ],
  // 计价方式（SKU 分页/详情吐 name）
  valuationType: [
    { value: 'BY_PIECE', label: '计件' },
    { value: 'BY_WEIGHT', label: '计重' }
  ],
  // 商品状态 name 版（Spu/Sku 已枚举化；unit/category/spec/price_rule 仍为
  // 数值，沿用 ENUMS.productStatus，键分开避免混用）
  productStatusName: [
    { value: 'NORMAL', label: '正常', type: 'success' },
    { value: 'DISABLED', label: '停用', type: 'info' }
  ],
  // 行级类型（物料/服务）
  itemType: [
    { value: 'MATERIAL', label: '物料' },
    { value: 'SERVICE', label: '服务' }
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
