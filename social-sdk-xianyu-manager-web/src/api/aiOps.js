import api from './request'

// ============== 批量上品 ==============
export function batchCreate(data) {
  return api.post('/ai/ops/batch-create', data)
}
export function getBatchProgress(taskId) {
  return api.get('/ai/ops/batch-create/progress', { params: { taskId } })
}

// ============== 多账号同步 ==============
export function multiSync(data) {
  return api.post('/ai/ops/multi-sync', data)
}

// ============== 运营周报 ==============
export function getWeeklyReport(accountId, modelId) {
  return api.get('/ai/ops/weekly-report', {
    params: { accountId, ...(modelId ? { modelId } : {}) }
  })
}

// ============== 任务查询 ==============
export function listTasks(accountId, status, page = 1, size = 20) {
  return api.get('/ai/ops/tasks', {
    params: { ...(accountId ? { accountId } : {}), ...(status ? { status } : {}), page, size }
  })
}

// ============== 运营知识库 ==============
export function listKnowledge(params = {}) {
  return api.get('/ai/ops/knowledge', { params })
}
export function getKnowledge(id) {
  return api.get(`/ai/ops/knowledge/${id}`)
}
export function createKnowledge(data) {
  return api.post('/ai/ops/knowledge', data)
}
export function deleteKnowledge(id) {
  return api.delete(`/ai/ops/knowledge/${id}`)
}

// ============== 运营建议 ==============
export function listSuggestions(params = {}) {
  return api.get('/ai/ops/suggestions', { params })
}
export function adoptSuggestion(id) {
  return api.post(`/ai/ops/suggestions/${id}/adopt`)
}
export function ignoreSuggestion(id) {
  return api.post(`/ai/ops/suggestions/${id}/ignore`)
}
