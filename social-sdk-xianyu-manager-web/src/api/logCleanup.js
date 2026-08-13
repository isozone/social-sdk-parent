import api from './request'

// ===== 日志清理统计 =====
export function getLogStats() {
  return api.get('/log-cleanup/stats')
}

// ===== 手动清理日志 =====
export function cleanupLogs(keepDays) {
  return api.post(`/log-cleanup/cleanup?keepDays=${keepDays}`)
}

// ===== 获取清理配置 =====
export function getLogCleanupConfig() {
  return api.get('/log-cleanup/config')
}
