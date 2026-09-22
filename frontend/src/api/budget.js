import request from '@/utils/request'

// ============================================================
// 预算 budget API（对齐 docs/P1主数据设计.md §3 与后端控制器）
// ============================================================

// ---- 预算科目（P-B1） ----
export function getBudgetSubjectTree() {
  return request.get('/api/v1/budgets/subjects/tree')
}
export function listBudgetSubjects() {
  return request.get('/api/v1/budgets/subjects/list')
}
export function getBudgetSubject(id) {
  return request.get(`/api/v1/budgets/subjects/${id}`)
}
export function createBudgetSubject(data) {
  return request.post('/api/v1/budgets/subjects', data)
}
export function updateBudgetSubject(id, data) {
  return request.put(`/api/v1/budgets/subjects/${id}`, data)
}
export function invalidateBudgetSubject(id) {
  return request.post(`/api/v1/budgets/subjects/${id}/invalidate`)
}

// ---- 预算项目（P-B2） ----
export function listBudgetProjects(params) {
  return request.get('/api/v1/budgets/projects/list', { params })
}
export function getBudgetProject(id) {
  return request.get(`/api/v1/budgets/projects/${id}`)
}
export function createBudgetProject(data) {
  return request.post('/api/v1/budgets/projects', data)
}
export function updateBudgetProject(id, data) {
  return request.put(`/api/v1/budgets/projects/${id}`, data)
}
export function invalidateBudgetProject(id) {
  return request.post(`/api/v1/budgets/projects/${id}/invalidate`)
}

// ---- 年度预算导入（P-B3） ----
export function previewBudgetImport(file) {
  const fd = new FormData()
  fd.append('file', file)
  return request.post('/api/v1/budgets/import/preview', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}
export function importBudget(file) {
  const fd = new FormData()
  fd.append('file', file)
  return request.post('/api/v1/budgets/import', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}
export function budgetImportTask(taskId) {
  return request.get(`/api/v1/budgets/import/tasks/${taskId}`)
}

// ---- 预算台账（P-B4） ----
export function pageBudgetHeaders(params) {
  return request.get('/api/v1/budgets/headers', { params })
}
export function listBudgetLines(headerId) {
  return request.get(`/api/v1/budgets/${headerId}/lines`)
}
