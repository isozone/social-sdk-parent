import { api } from './request'

export interface OpenListItem {
  id: number
  name: string
  type: string
  status: 'running' | 'stopped' | 'error'
  port?: number
  updatedAt: string
}

export function getStatus() {
  return api.get<OpenListItem[]>('/api/mini/open-list/status')
}

export function start(id: number | string) {
  return api.post(`/api/mini/open-list/start/${id}`)
}

export function stop(id: number | string) {
  return api.post(`/api/mini/open-list/stop/${id}`)
}

export function restart(id: number | string) {
  return api.post(`/api/mini/open-list/restart/${id}`)
}
