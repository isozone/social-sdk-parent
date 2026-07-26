import { api } from './request'

// 后端真实端点：ProxyController /api/proxy + @GetMapping("/config")
export function getConfig() {
  return api.get('/api/mini/proxy/config')
}

// 后端真实端点：ProxyController /api/proxy + @PostMapping("/config")
export function saveConfig(data: any) {
  return api.post('/api/mini/proxy/config', data)
}

// 后端真实端点：ProxyController /api/proxy + @GetMapping("/status")
export function getStatus() {
  return api.get('/api/mini/proxy/status')
}

// 后端真实端点：ProxyController /api/proxy + @PostMapping("/reload")
export function reload() {
  return api.post('/api/mini/proxy/reload')
}

// 后端真实端点：ProxyController /api/proxy + @GetMapping("/health-check")
export function healthCheck() {
  return api.get('/api/mini/proxy/health-check')
}
