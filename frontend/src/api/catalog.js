import request from '@/utils/request'

// ============================================================
// 商品库 catalog API（对齐 docs/P1主数据设计.md §3 与后端控制器）
// 统一响应体 { code, message, data, traceId }，request 拦截器已解包到 res.data 层。
// ============================================================

// ---- 品类（P-C1） ----
export function getCategoryTree() {
  return request.get('/api/v1/catalog/categories/tree')
}
export function listCategories() {
  return request.get('/api/v1/catalog/categories/list')
}
export function getCategory(id) {
  return request.get(`/api/v1/catalog/categories/${id}`)
}
export function createCategory(data) {
  return request.post('/api/v1/catalog/categories', data)
}
export function updateCategory(id, data) {
  return request.put(`/api/v1/catalog/categories/${id}`, data)
}
export function invalidateCategory(id) {
  return request.post(`/api/v1/catalog/categories/${id}/invalidate`)
}
// 递归统计每个品类下（含下级）关联的有效 SPU 数量：Map<categoryId, count>
export function getCategoryProductCounts() {
  return request.get('/api/v1/catalog/categories/product-counts')
}

// ---- 单位（P-C2） ----
export function pageUnits(params) {
  return request.get('/api/v1/catalog/units/page', { params })
}
export function listUnits(params) {
  return request.get('/api/v1/catalog/units/list', { params })
}
export function getUnit(id) {
  return request.get(`/api/v1/catalog/units/${id}`)
}
export function createUnit(data) {
  return request.post('/api/v1/catalog/units', data)
}
export function updateUnit(id, data) {
  return request.put(`/api/v1/catalog/units/${id}`, data)
}
export function invalidateUnit(id) {
  return request.post(`/api/v1/catalog/units/${id}/invalidate`)
}
export function enableUnit(id) {
  return request.post(`/api/v1/catalog/units/${id}/enable`)
}
// 删除单位（逻辑删除；被 SKU/换算/绑定引用时后端拒绝）
export function deleteUnit(id) {
  return request.delete(`/api/v1/catalog/units/${id}`)
}

// ---- 规格（P-C3） ----
export function listSpecOptions(params) {
  return request.get('/api/v1/catalog/spec-options/list', { params })
}
export function listSpecGrouped() {
  return request.get('/api/v1/catalog/spec-options/grouped')
}
export function getSpecOption(id) {
  return request.get(`/api/v1/catalog/spec-options/${id}`)
}
export function createSpecOption(data) {
  return request.post('/api/v1/catalog/spec-options', data)
}
export function updateSpecOption(id, data) {
  return request.put(`/api/v1/catalog/spec-options/${id}`, data)
}
export function invalidateSpecOption(id) {
  return request.post(`/api/v1/catalog/spec-options/${id}/invalidate`)
}
export function enableSpecOption(id) {
  return request.post(`/api/v1/catalog/spec-options/${id}/enable`)
}
// 删除单个规格值（逻辑删除；被 SKU 引用时后端拒绝）
export function deleteSpecOption(id) {
  return request.delete(`/api/v1/catalog/spec-options/${id}`)
}

// ---- 价格规则（P-C4） ----
export function listPriceRules(params) {
  return request.get('/api/v1/catalog/price-rules/list', { params })
}
export function getPriceRule(id) {
  return request.get(`/api/v1/catalog/price-rules/${id}`)
}
export function createPriceRule(data) {
  return request.post('/api/v1/catalog/price-rules', data)
}
export function updatePriceRule(id, data) {
  return request.put(`/api/v1/catalog/price-rules/${id}`, data)
}
export function invalidatePriceRule(id) {
  return request.post(`/api/v1/catalog/price-rules/${id}/invalidate`)
}
// 价格校验：refType(1商品/2品类) + refId + price → 通过返回 data=true，越界后端抛业务错误
export function validatePrice(params) {
  return request.get('/api/v1/catalog/price-rules/validate', { params })
}
// 采购最低价同步标准价：读取开关状态 + 上次更新数量
export function getLowestOrderSync() {
  return request.get('/api/v1/catalog/price-rules/lowest-order-sync')
}
// 采购最低价同步标准价：持久化开关；开启时按最低采购单价回写 SPU 标准价，返回更新条数
export function applyLowestOrderSync(enabled) {
  return request.post('/api/v1/catalog/price-rules/lowest-order-sync', null, { params: { enabled } })
}

// ---- SPU（P-C5） ----
export function pageSpus(params) {
  return request.get('/api/v1/catalog/spus/page', { params })
}
export function getSpu(id) {
  return request.get(`/api/v1/catalog/spus/${id}`)
}
export function listSkusBySpu(spuId) {
  return request.get(`/api/v1/catalog/spus/${spuId}/skus`)
}
export function createSpu(data) {
  return request.post('/api/v1/catalog/spus', data)
}
export function updateSpu(id, data) {
  return request.put(`/api/v1/catalog/spus/${id}`, data)
}
export function enableSpu(id) {
  return request.post(`/api/v1/catalog/spus/${id}/enable`)
}
export function disableSpu(id) {
  return request.post(`/api/v1/catalog/spus/${id}/disable`)
}

// ---- SKU（P-C5） ----
export function pageSkus(params) {
  return request.get('/api/v1/catalog/skus/page', { params })
}
export function getSku(id) {
  return request.get(`/api/v1/catalog/skus/${id}`)
}
export function createSku(data) {
  return request.post('/api/v1/catalog/skus', data)
}
export function updateSku(id, data) {
  return request.put(`/api/v1/catalog/skus/${id}`, data)
}
export function enableSku(id) {
  return request.post(`/api/v1/catalog/skus/${id}/enable`)
}
export function disableSku(id) {
  return request.post(`/api/v1/catalog/skus/${id}/disable`)
}
export function deleteSku(id) {
  return request.delete(`/api/v1/catalog/skus/${id}`)
}

// ---- 单位换算（P-C5） ----
export function listUnitConversions(params) {
  return request.get('/api/v1/catalog/unit-conversions', { params })
}
export function currentUnitConversion(params) {
  return request.get('/api/v1/catalog/unit-conversions/current', { params })
}
export function saveUnitConversion(data) {
  return request.post('/api/v1/catalog/unit-conversions', data)
}

// ---- 产品导入 / 导出（P-C5 / P-C6） ----
export function importSingle(file) {
  return uploadFileTo('/api/v1/catalog/spus/import/single', file)
}
export function previewMulti(file) {
  return uploadFileTo('/api/v1/catalog/spus/import/multi/preview', file)
}
export function importMulti(file) {
  return uploadFileTo('/api/v1/catalog/spus/import/multi', file)
}
export function exportSpus(data) {
  return request.post('/api/v1/catalog/spus/export', data)
}
export function exportTaskStatus(taskId) {
  return request.get(`/api/v1/catalog/spus/export/${taskId}`)
}
// 下载需带 Authorization，故用 blob，由调用方 createObjectURL 保存
export function exportDownload(taskId) {
  return request.get(`/api/v1/catalog/spus/export/${taskId}/download`, { responseType: 'blob' })
}

// ---- 通用文件上传（前置 R1） ----
export function uploadFile(file) {
  return uploadFileTo('/api/v1/files/upload', file)
}

/** 以 multipart/form-data 上传单个文件（字段名固定 file）。 */
export function uploadFileTo(url, file) {
  const fd = new FormData()
  fd.append('file', file)
  return request.post(url, fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}
