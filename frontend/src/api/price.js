import request from '@/utils/request'

// ============================================================
// P3 价格库 API（对齐 PriceHistoryController /api/v1/price-histories）
// 枚举值均为后端枚举名字符串（auditStatus: PENDING/APPROVED/REJECTED；
// source: QUOTATION/AWARD/ORDER/MANUAL）
// ============================================================

// 待审价列表（异常价人工复核队列；后端返回 PageResult<PriceHistory> = data{records,total,current,size}）
export function getPricePending(current = 1, size = 10) {
  return request.get('/api/v1/price-histories/pending', { params: { current, size } })
}

// 某 SKU 近期通过价（供审核时参考比对；limit 默认 5）
export function getPriceRecent(skuId, limit = 5) {
  return request.get('/api/v1/price-histories/recent', { params: { skuId, limit } })
}

// 审核待审价（通过/驳回；仅 PENDING 可流转；body { approved, remark }）
export function auditPrice(id, data) {
  return request.post(`/api/v1/price-histories/${id}/audit`, data)
}
