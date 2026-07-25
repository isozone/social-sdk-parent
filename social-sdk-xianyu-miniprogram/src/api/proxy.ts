import { api } from './request'

export interface ProxyStatus {
  online: number
  total: number
}

export interface ProxyConfig {
  host: string
  port: number
  username?: string
  password?: string
  type: 'HTTP' | 'HTTPS' | 'SOCKS5'
}

export function getStatus() {
  return api.get<ProxyStatus>('/api/mini/monitor/proxy/status')
}

export function getConfig() {
  return api.get<ProxyConfig>('/api/mini/monitor/proxy/config')
}

export function updateConfig(config: ProxyConfig) {
  return api.put('/api/mini/monitor/proxy/config', config)
}

export function healthCheck(proxyId?: number | string) {
  return api.post(`/api/mini/monitor/proxy/health-check${proxyId ? `/${proxyId}` : ''}`)
}
