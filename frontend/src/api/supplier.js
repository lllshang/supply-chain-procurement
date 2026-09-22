import request from '@/utils/request'

// ============================================================
// 供应商 supplier API（对齐 docs/P1主数据设计.md §3 与后端控制器）
// ============================================================

// ---- 供应商分类（P-S1） ----
export function getSupplierCategoryTree() {
  return request.get('/api/v1/suppliers/categories/tree')
}
export function listSupplierCategories() {
  return request.get('/api/v1/suppliers/categories/list')
}
export function getSupplierCategory(id) {
  return request.get(`/api/v1/suppliers/categories/${id}`)
}
export function createSupplierCategory(data) {
  return request.post('/api/v1/suppliers/categories', data)
}
export function updateSupplierCategory(id, data) {
  return request.put(`/api/v1/suppliers/categories/${id}`, data)
}
export function invalidateSupplierCategory(id) {
  return request.post(`/api/v1/suppliers/categories/${id}/invalidate`)
}

// ---- 供应商档案（P-S2） ----
export function pageSuppliers(params) {
  return request.get('/api/v1/suppliers/page', { params })
}
export function getSupplier(id) {
  return request.get(`/api/v1/suppliers/${id}`)
}
export function createSupplier(data) {
  return request.post('/api/v1/suppliers', data)
}
export function updateSupplier(id, data) {
  return request.put(`/api/v1/suppliers/${id}`, data)
}
export function updateSupplierCoopStatus(id, status) {
  return request.post(`/api/v1/suppliers/${id}/coop-status`, null, { params: { status } })
}
export function markSupplierBlacklist(id, blacklist) {
  return request.post(`/api/v1/suppliers/${id}/blacklist`, null, { params: { blacklist } })
}
export function getSupplierAdmission(id) {
  return request.get(`/api/v1/suppliers/${id}/admission`)
}

// ---- 供应商-商品绑定（P-S3） ----
export function listSupplierSkus(params) {
  return request.get('/api/v1/supplier-skus', { params })
}
export function bindSupplierSku(data) {
  return request.post('/api/v1/supplier-skus', data)
}
export function unbindSupplierSku(id) {
  return request.delete(`/api/v1/supplier-skus/${id}`)
}
export function batchBindSupplierSku(file) {
  const fd = new FormData()
  fd.append('file', file)
  return request.post('/api/v1/supplier-skus/batch', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}

// ---- 资质管理 + 审核闭环（P-S4） ----
export function listQuals(supplierId) {
  return request.get(`/api/v1/suppliers/${supplierId}/quals`)
}
export function createQual(supplierId, data) {
  return request.post(`/api/v1/suppliers/${supplierId}/quals`, data)
}
export function updateQual(supplierId, id, data) {
  return request.put(`/api/v1/suppliers/${supplierId}/quals/${id}`, data)
}
// 发起审批：返回 taskId
export function reviewQual(supplierId, id) {
  return request.post(`/api/v1/suppliers/${supplierId}/quals/${id}/review`)
}
// 驳回后修改重提
export function resubmitQual(supplierId, id, data) {
  return request.post(`/api/v1/suppliers/${supplierId}/quals/${id}/resubmit`, data)
}
// 审核落地：callback(taskId, approved, comment)
export function qualCallback(supplierId, params) {
  return request.post(`/api/v1/suppliers/${supplierId}/quals/callback`, null, { params })
}
