import request from '@/utils/request'

// 采购申请列表（分页）
export function pagePurchaseRequests(params) {
  return request.get('/api/v1/purchase-requests/page', { params })
}

// 新建采购申请
export function createPurchaseRequest(data) {
  return request.post('/api/v1/purchase-requests', data)
}
