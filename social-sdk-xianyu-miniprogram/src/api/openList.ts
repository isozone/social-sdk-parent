import { api } from './request'

// 后端真实端点：OpenListController /api/cloud-storage/openlist + @GetMapping("/status")
export function getStatus() {
  return api.get<any>('/api/mini/cloud-storage/openlist/status')
}

// 后端真实端点：OpenListController /api/cloud-storage/openlist + @PostMapping("/install")
export function install() {
  return api.post('/api/mini/cloud-storage/openlist/install')
}

// 后端真实端点：OpenListController /api/cloud-storage/openlist + @PostMapping("/start")
export function start() {
  return api.post('/api/mini/cloud-storage/openlist/start')
}

// 后端真实端点：OpenListController /api/cloud-storage/openlist + @PostMapping("/stop")
export function stop() {
  return api.post('/api/mini/cloud-storage/openlist/stop')
}

// 后端真实端点：OpenListController /api/cloud-storage/openlist + @PostMapping("/restart")
export function restart() {
  return api.post('/api/mini/cloud-storage/openlist/restart')
}

// 后端真实端点：OpenListController /api/cloud-storage/openlist + @GetMapping("/progress")
export function getProgress() {
  return api.get('/api/mini/cloud-storage/openlist/progress')
}

// 后端真实端点：OpenListController /api/cloud-storage/openlist + @GetMapping("/events")
export function getEvents(params?: any) {
  return api.get<any[]>('/api/mini/cloud-storage/openlist/events', params)
}

// 后端真实端点：OpenListController /api/cloud-storage/openlist + @GetMapping("/storages")
export function getStorages() {
  return api.get<any[]>('/api/mini/cloud-storage/openlist/storages')
}

// 后端真实端点：OpenListController /api/cloud-storage/openlist + @PostMapping("/storages")
export function addStorage(data: any) {
  return api.post('/api/mini/cloud-storage/openlist/storages', data)
}

// 后端真实端点：OpenListController /api/cloud-storage/openlist + @GetMapping("/drivers")
export function getDrivers() {
  return api.get<any[]>('/api/mini/cloud-storage/openlist/drivers')
}

// 后端真实端点：OpenListController /api/cloud-storage/openlist + @GetMapping("/drivers/list")
export function getDriversList() {
  return api.get<any[]>('/api/mini/cloud-storage/openlist/drivers/list')
}

// 后端真实端点：OpenListController /api/cloud-storage/openlist + @GetMapping("/files")
export function getFiles(params?: any) {
  return api.get<any[]>('/api/mini/cloud-storage/openlist/files', params)
}
