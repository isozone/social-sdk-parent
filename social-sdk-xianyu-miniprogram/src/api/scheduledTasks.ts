import { api } from './request'

function qs(params?: any) {
  if (!params) return ''
  const pairs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
  return pairs.length ? `?${pairs.join('&')}` : ''
}

export function getTasks(params?: any) {
  return api.get<any>('/api/mini/scheduled-tasks', params)
}

export function getTask(taskKey: string) {
  return api.get<any>(`/api/mini/scheduled-tasks/${taskKey}`)
}

export function toggleTask(taskKey: string, enabled: boolean) {
  return api.put(`/api/mini/scheduled-tasks/${taskKey}/toggle${qs({ enabled })}`)
}

export function updateCron(taskKey: string, cron: string) {
  return api.put(`/api/mini/scheduled-tasks/${taskKey}/cron${qs({ cron })}`)
}

export function runTask(taskKey: string) {
  return api.post(`/api/mini/scheduled-tasks/${taskKey}/run`)
}
