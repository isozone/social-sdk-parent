import api from './request'

// ===== 仪表盘统计 =====
export function getDashboard() {
  return api.get('/monitor/dashboard')
}

// ===== 账号维度统计 =====
export function getAccountStats() {
  return api.get('/monitor/accounts')
}

// ===== 监控结果统计 =====
export function getStatsOverview() {
  return api.get('/monitor/results/stats')
}

// ===== 清缓存 =====
export function clearCache() {
  return api.post('/monitor/cache/clear')
}

// ===== Monitor 配置（兼容旧路径，实则回到监控概览）=====
export function getMonitorConfig() {
  return api.get('/monitor/dashboard')
}
