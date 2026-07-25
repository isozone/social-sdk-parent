import { api } from './request'
import type { PageResponse } from '@/types/common'

export interface CircuitBreakerItem {
  id: number
  target: string
  action: string
  count: number
  rate: number
  triggered: boolean
  lastTriggeredAt?: string
}

export function getList(params?: any) {
  return api.get<PageResponse<CircuitBreakerItem>>('/api/mini/monitor/circuit-breaker', params)
}

export function reset(id: number | string) {
  return api.post(`/api/mini/monitor/circuit-breaker/${id}/reset`)
}

export function globalReset() {
  return api.post('/api/mini/monitor/circuit-breaker/global-reset')
}
