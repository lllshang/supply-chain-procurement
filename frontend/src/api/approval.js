import request from '@/utils/request'

// ============================================================
// P4 审批中心工作台 API（对齐后端 ApprovalTaskController / ApprovalFlowDefController
// / NoticeController，docs/P4审批中心设计.md §6）
// 枚举值均为后端枚举名字符串（#33 契约）；ID 全程字符串零 Number() 转换（#28/#1 契约）
// ============================================================

// ---- 审批任务工作台（/api/v1/approvals/tasks） ----
// 待办分页（服务端按当前节点候选人过滤）
export function listTodoTasks(params) {
  return request.get('/api/v1/approvals/tasks/todo', { params })
}
// 已办分页（我的审批记录）
export function listDoneTasks(params) {
  return request.get('/api/v1/approvals/tasks/done', { params })
}
// 任务详情（节点链时间轴 + 审批记录 + canApprove）
export function getTaskDetail(taskId) {
  return request.get(`/api/v1/approvals/tasks/${taskId}/detail`)
}
// 同意（comment 可空）
export function approveTask(taskId, comment) {
  return request.post(`/api/v1/approvals/tasks/${taskId}/approve`, { comment })
}
// 驳回（comment 必填，后端双拦截）
export function rejectTask(taskId, comment) {
  return request.post(`/api/v1/approvals/tasks/${taskId}/reject`, { comment })
}
// 旧任务筛选（退役兼容，新页面勿用）
export function searchApprovalTasks(params) {
  return request.get('/api/v1/approvals/tasks/search', { params })
}

// ---- 流程配置（/api/v1/approvals/flows，菜单 1003） ----
export function listFlows() {
  return request.get('/api/v1/approvals/flows')
}
export function createFlow(data) {
  return request.post('/api/v1/approvals/flows', data)
}
export function updateFlow(flowKey, data) {
  return request.put(`/api/v1/approvals/flows/${flowKey}`, data)
}
export function updateFlowNode(flowKey, nodeId, data) {
  return request.put(`/api/v1/approvals/flows/${flowKey}/nodes/${nodeId}`, data)
}

// ---- 站内通知（/api/v1/notices） ----
export function unreadCount() {
  return request.get('/api/v1/notices/unread-count')
}
export function pageNotices(params) {
  return request.get('/api/v1/notices', { params })
}
export function markNoticeRead(id) {
  return request.post(`/api/v1/notices/${id}/read`)
}
export function readAllNotices() {
  return request.post('/api/v1/notices/read-all')
}
