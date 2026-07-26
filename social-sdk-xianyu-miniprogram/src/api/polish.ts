import { api } from './request'

export function runPolish() {
  return api.post<number>('/api/mini/polish/run')
}

export function getPolishLogs(params?: any) {
  return api.get<any>('/api/mini/polish/logs', params)
}
