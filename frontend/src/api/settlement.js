import request from '@/utils/request'

// ============================================================
// P3 结算与付款 API（对齐 SettlementController / PaymentController）
// 枚举值均为后端枚举名字符串（如 'PENDING' / 'ONE_TIME'）
// ============================================================

// ---- 结算单（/api/v1/settlements） ----
export function pageSettlements(params) {
  return request.get('/api/v1/settlements/page', { params })
}
export function getSettlement(id) {
  return request.get(`/api/v1/settlements/${id}`)
}
// 订单入口带出草稿（可结余量/建议金额/服务扣款预填）
export function draftFromOrder(orderId) {
  return request.get(`/api/v1/settlements/draft/from-order/${orderId}`)
}
// 到货单入口带出草稿（优先；重复结算拦截以到货单为粒度）
export function draftFromArrival(arrivalId) {
  return request.get(`/api/v1/settlements/draft/from-arrival/${arrivalId}`)
}
export function createSettlement(data) {
  return request.post('/api/v1/settlements', data)
}
// 修改重提（驳回留痕后）
export function updateSettlement(id, data) {
  return request.put(`/api/v1/settlements/${id}`, data)
}
// 提交 SETTLEMENT 审批（通过后触发预算核销 writeOff）
export function submitSettlement(id) {
  return request.post(`/api/v1/settlements/${id}/submit`)
}

/** B9：作废结算单（仅 PENDING 可作废，释放 committed 口径） */
export function voidSettlement(id, data) {
  return request.post(`/api/v1/settlements/${id}/void`, data)
}

// ---- 预付款结算（D10：从订单发起，含在途封顶校验） ----
// 预付款草稿预览（订单金额/已付预付款 prepaidPaid/可发起余额）
export function draftPrepaymentFromOrder(orderId) {
  return request.get(`/api/v1/settlements/prepayment/draft/${orderId}`)
}
// 创建预付款结算单（超"订单有效金额−累计预付款(含在途)"返回 3000/4000 原样 toast）
export function createPrepaymentSettlement(orderId, data) {
  return request.post(`/api/v1/settlements/prepayment/${orderId}`, data)
}

// ---- 付款登记（/api/v1/payments） ----
export function pagePayments(params) {
  return request.get('/api/v1/payments/page', { params })
}
export function getPayment(id) {
  return request.get(`/api/v1/payments/${id}`)
}
export function createPayment(data) {
  return request.post('/api/v1/payments', data)
}
// 修改重提
export function updatePayment(id, data) {
  return request.put(`/api/v1/payments/${id}`, data)
}
// 线下付款登记确认（R6：免审批；凭证+日期）→ PAID
export function confirmPayment(id, data) {
  return request.post(`/api/v1/payments/${id}/confirm`, data)
}
// G4：作废/冲销付款单（复刻 B9 Settlement VOIDED 范式）
export function voidPayment(id, data) {
  return request.post(`/api/v1/payments/${id}/void`, data)
}
// 供应商对账单（应付/已付/差额 + 明细）
export function getStatement(params) {
  return request.get('/api/v1/payments/statement', { params })
}
// 供应商对账单 Excel 导出（拦截器对 blob 直接放行，返回 Blob）
export function exportStatement(params) {
  return request.get('/api/v1/payments/statement/export', { params, responseType: 'blob' })
}
