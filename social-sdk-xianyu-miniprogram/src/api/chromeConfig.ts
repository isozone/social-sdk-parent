import { api } from './request'

export interface ChromeConfig {
  enabled: boolean
  headless: boolean
  userAgent?: string
}

export function detect() {
  return api.post<ChromeConfig>('/api/mini/monitor/chrome/detect')
}

export function save(config: ChromeConfig) {
  return api.post('/api/mini/monitor/chrome/save', config)
}
