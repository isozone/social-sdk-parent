import { api } from './request'

// 后端真实端点：ChromeConfigController /api/chrome-config + @GetMapping("/detect")
export function detect() {
  return api.get('/api/mini/chrome-config/detect')
}

// 后端真实端点：ChromeConfigController /api/chrome-config + @GetMapping("/detect/all")
export function detectAll() {
  return api.get('/api/mini/chrome-config/detect/all')
}

// 后端真实端点：ChromeConfigController /api/chrome-config + @PostMapping("/save")
export function save(data: any) {
  return api.post('/api/mini/chrome-config/save', data)
}

// 后端真实端点：ChromeConfigController /api/chrome-config + @PostMapping("/download")
export function download(data: any) {
  return api.post('/api/mini/chrome-config/download', data)
}

// 后端真实端点：ChromeConfigController /api/chrome-config + @PostMapping("/validate")
export function validate(data: any) {
  return api.post('/api/mini/chrome-config/validate', data)
}
