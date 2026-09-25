import request from '@/utils/request'

// ============================================================
// P2 采购主链路 API（对齐后端控制器 / docs/P2采购主链路设计.md §5）
// 枚举值均为后端枚举名字符串（如 'APPROVED'）
// ============================================================

// ---- 采购申请（/api/v1/purchase-requests） ----
export function pageApplies(params) {
  return request.get('/api/v1/purchase-requests/page', { params })
}
export function getApply(id) {
  return request.get(`/api/v1/purchase-requests/${id}`)
}
export function getApplyDetail(id) {
  return request.get(`/api/v1/purchase-requests/${id}/items`)
}
export function createApply(data) {
  return request.post('/api/v1/purchase-requests', data)
}
export function updateApply(id, data) {
  return request.put(`/api/v1/purchase-requests/${id}`, data)
}
export function submitApply(id) {
  return request.post(`/api/v1/purchase-requests/${id}/submit`)
}
// 导出申请单（OOXML 双 Sheet）；拦截器对 blob 直接放行，返回 Blob
export function exportApply(id) {
  return request.get(`/api/v1/purchase-requests/${id}/export`, { responseType: 'blob' })
}

// ---- 常购清单（/api/v1/frequent-purchases） ----
export function listFrequent(deptId) {
  return request.get('/api/v1/frequent-purchases', { params: { deptId } })
}
export function frequentBringIn(data) {
  return request.post('/api/v1/frequent-purchases/bring-in', data)
}

// ---- 询价（/api/v1/inquiries） ----
export function pageInquiries(params) {
  return request.get('/api/v1/inquiries/page', { params })
}
export function createInquiry(data) {
  return request.post('/api/v1/inquiries', data)
}
export function publishInquiry(id, supplierIds) {
  return request.post(`/api/v1/inquiries/${id}/publish`, supplierIds || [])
}
export function closeInquiry(id) {
  return request.post(`/api/v1/inquiries/${id}/close`)
}
export function cancelInquiry(id) {
  return request.post(`/api/v1/inquiries/${id}/cancel`)
}
export function listInquirySuppliers(id) {
  return request.get(`/api/v1/inquiries/${id}/suppliers`)
}
export function addInquirySuppliers(id, supplierIds) {
  return request.post(`/api/v1/inquiries/${id}/suppliers`, supplierIds)
}
export function removeInquirySupplier(id, supplierId) {
  return request.delete(`/api/v1/inquiries/${id}/suppliers/${supplierId}`)
}
export function getInquiryComparison(id) {
  return request.get(`/api/v1/inquiries/${id}/comparison`)
}
// 报价包模板（xlsx）
export function downloadQuotationTemplate(inquiryId) {
  return request.get(`/api/v1/inquiries/${inquiryId}/quotation-template`, { responseType: 'blob' })
}

// ---- 报价（/api/v1/quotations） ----
export function listQuotations(inquiryId) {
  return request.get('/api/v1/quotations', { params: { inquiryId } })
}
export function importQuotations(inquiryId, file) {
  const fd = new FormData()
  fd.append('file', file)
  return request.post(`/api/v1/quotations/import?inquiryId=${inquiryId}`, fd, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
export function acceptQuotation(id) {
  return request.post(`/api/v1/quotations/${id}/accept`)
}
export function rejectQuotation(id) {
  return request.post(`/api/v1/quotations/${id}/reject`)
}

// ---- 定标（/api/v1/awards） ----
export function pageAwards(params) {
  return request.get('/api/v1/awards/page', { params })
}
export function getAward(id) {
  return request.get(`/api/v1/awards/${id}`)
}
export function createAward(data) {
  return request.post('/api/v1/awards', data)
}
export function updateAwardItems(id, data) {
  return request.put(`/api/v1/awards/${id}/items`, data)
}
export function submitAward(id) {
  return request.post(`/api/v1/awards/${id}/submit`)
}
export function listAwardItems(id) {
  return request.get(`/api/v1/awards/${id}/items`)
}

// ---- 合同（/api/v1/contracts） ----
export function pageContracts(params) {
  return request.get('/api/v1/contracts/page', { params })
}
export function createContract(data) {
  return request.post('/api/v1/contracts', data)
}
export function updateContract(id, data) {
  return request.put(`/api/v1/contracts/${id}`, data)
}
export function submitContract(id) {
  return request.post(`/api/v1/contracts/${id}/submit`)
}
export function terminateContract(id, reason) {
  return request.post(`/api/v1/contracts/${id}/terminate`, { reason })
}
export function renewContract(id, data) {
  return request.post(`/api/v1/contracts/${id}/renew`, data)
}
// P4 R3a：补充签订（关联原合同，独立走 CONTRACT 审批）
export function supplementContract(id, data) {
  return request.post(`/api/v1/contracts/${id}/supplement`, data)
}
// P4 R3a：合同类型字典（contract:type:read/write）
export function listContractTypes() {
  return request.get('/api/v1/contract-types')
}
export function createContractType(data) {
  return request.post('/api/v1/contract-types', data)
}
export function updateContractType(id, data) {
  return request.put(`/api/v1/contract-types/${id}`, data)
}
export function deleteContractType(id) {
  return request.delete(`/api/v1/contract-types/${id}`)
}
// P4 D15：合同 SKU 白名单（覆盖式维护；空列表=清空，恢复现网额度闸兜底行为）
export function listSkuWhitelist(contractId) {
  return request.get(`/api/v1/contracts/${contractId}/sku-whitelist`)
}
export function replaceSkuWhitelist(contractId, items) {
  return request.put(`/api/v1/contracts/${contractId}/sku-whitelist`, items)
}

export function listExpiringContracts() {
  return request.get('/api/v1/contracts/expiring-warn')
}

// ---- 订单（/api/v1/orders） ----
export function pageOrders(params) {
  return request.get('/api/v1/orders/page', { params })
}
export function createOrder(data) {
  return request.post('/api/v1/orders', data)
}
export function listOrderItems(id) {
  return request.get(`/api/v1/orders/${id}/items`)
}
export function getOrderTrace(id) {
  return request.get(`/api/v1/orders/${id}/trace`)
}
export function changeOrder(id, data) {
  return request.post(`/api/v1/orders/${id}/change`, data)
}
export function cancelOrder(id, reason) {
  return request.post(`/api/v1/orders/${id}/cancel`, { reason })
}
export function listOrderChanges(id) {
  return request.get(`/api/v1/orders/${id}/changes`)
}

// ---- 到货验收（/api/v1/arrivals） ----
export function pageArrivals(params) {
  return request.get('/api/v1/arrivals/page', { params })
}
export function createArrival(data) {
  return request.post('/api/v1/arrivals', data)
}
export function listArrivalItems(id) {
  return request.get(`/api/v1/arrivals/${id}/items`)
}
export function storeArrivalItem(itemId, qtyStored) {
  return request.post(`/api/v1/arrivals/items/${itemId}/store`, { qtyStored })
}
export function handleArrivalItem(itemId, type) {
  return request.post(`/api/v1/arrivals/items/${itemId}/handle`, { type })
}
export function pageArrivalLedger(params) {
  return request.get('/api/v1/arrivals/ledger', { params })
}

// ---- 服务考核（/api/v1/service-assesses） ----
export function pageAssesses(params) {
  return request.get('/api/v1/service-assesses/page', { params })
}
export function createAssess(data) {
  return request.post('/api/v1/service-assesses', data)
}
export function listAssessByOrder(orderId) {
  return request.get(`/api/v1/service-assesses/order/${orderId}`)
}

// ---- 履约调整（/api/v1/fulfillment-adjusts） ----
export function pageAdjusts(params) {
  return request.get('/api/v1/fulfillment-adjusts/page', { params })
}
export function createAdjust(data) {
  return request.post('/api/v1/fulfillment-adjusts', data)
}
export function submitAdjust(id) {
  return request.post(`/api/v1/fulfillment-adjusts/${id}/submit`)
}

// ---- 审批中心（/api/v1/approvals/tasks）----
// P4 迁移至 @/api/approval（保留旧导出兼容）
export { searchApprovalTasks, approveTask, rejectTask } from '@/api/approval'

// ---- 通用下载：Blob 保存为文件 ----
export function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

// errorSheetBase64（xlsx）解码并下载（#24 错误 Sheet）
export function downloadErrorSheet(base64, filename) {
  const bin = atob(base64)
  const bytes = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i += 1) {
    bytes[i] = bin.charCodeAt(i)
  }
  saveBlob(new Blob([bytes], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }), filename)
}

// B4：错误 Sheet 独立文件流下载（按批次号，后端流式返回 xlsx；与内联 base64 方案互斥）
export async function downloadErrorSheetByBatch(batchNo, filename) {
  const blob = await request.get(`/api/v1/quotations/import/error-sheet/${batchNo}`, {
    responseType: 'blob'
  })
  saveBlob(blob, filename || `quotation-error-sheet-${batchNo}.xlsx`)
}
